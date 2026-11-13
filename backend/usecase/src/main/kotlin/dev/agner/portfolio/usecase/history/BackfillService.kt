package dev.agner.portfolio.usecase.history

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.history.model.BackfillReport
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.CustodySnapshotService
import dev.agner.portfolio.usecase.listedasset.GapService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.CustodyPosition
import dev.agner.portfolio.usecase.listedasset.model.OpeningSeedCreation
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.model.isImported
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

// The history backfill: it turns the adoption seeds and the custody cross-check into a ledger the
// series can be replayed from. Absorb retires each OPENING seed the imported history now covers;
// reconcile retires a GAP a true trade has replaced; the custody snapshot records a GAP for a delta
// still unexplained. Every touched ticker's projection is replayed once, so Position, average price
// and the GAP replay agree before the run returns.
@Service
class BackfillService(
    private val trades: ITradeRepository,
    private val positions: PositionService,
    private val gapService: GapService,
    private val custodySnapshot: CustodySnapshotService,
    private val institutions: InstitutionService,
    private val transaction: ITransactionTemplate,
) {

    // Absorbing and detecting share one transaction: a run either leaves the ledger and its
    // projections consistent or changes nothing. `custody` is the end-of-window Posição export;
    // an empty list runs the seed absorption alone.
    suspend fun backfill(custody: List<CustodyPosition>): BackfillReport = transaction.execute {
        val absorbed = absorb()
        val replaced = gapService.reconcileAll()

        // The projection the delta is measured against must already include the open GAPs, so the
        // replay happens before detection rather than after it.
        absorbed.tickers.forEach { positions.reproject(it) }

        // Every ledger ticker is replayed, not only the ones this run touched: the report's
        // `positionsReprojected` is the evidence that the whole book was rebuilt, and a run that
        // absorbed nothing would otherwise read as a no-op.
        val ledgerTrades = trades.fetchAll()
        val ledgerTickers = ledgerTrades.map { it.ticker }.toSet()
        val created = custodySnapshot.openGaps(custody)
        val touched = absorbed.tickers.toSet() + ledgerTickers + created.map { it.ticker }
        touched.forEach { positions.reproject(it) }

        BackfillReport(
            absorbedSeeds = absorbed.absorbed,
            adjustedSeeds = absorbed.adjusted,
            gapsCreated = created.size,
            gapsReplaced = replaced,
            positionsReprojected = touched.size,
        )
    }

    // The adoption write: one OPENING trade per seeded Position at its closing price, tagged with a
    // dedicated institution so provenance is unambiguous. The backfill can only absorb seeds that
    // exist, so this is the production entry point that creates them.
    suspend fun seedOpenings(seeds: List<OpeningSeedCreation>): List<Trade> = transaction.execute {
        if (seeds.isEmpty()) return@execute emptyList<Trade>()

        val institution = institutions.create(InstitutionCreation(ADOPTION_INSTITUTION))
        seeds.map { seed ->
            val ticker = seed.ticker.normalizedTicker()
            val trade = trades.save(
                creation = TradeCreation(
                    ticker = ticker,
                    side = TradeSide.BUY,
                    date = seed.date,
                    venue = seed.venue,
                    quantity = seed.quantity,
                    unitPrice = seed.unitPrice,
                    institutionId = institution.id,
                ),
                source = TradeSource.OPENING,
                ordinal = trades.fetchAllBy(ticker).size + 1,
            )
            positions.reproject(ticker)
            trade
        }
    }

    // An OPENING seed represents the custody adopted before the export window. Once imported trades
    // dated before the seed cover its quantity, the seed is history — retire it. Partial coverage
    // reduces the seed to the uncovered portion (the ticker keeps its seed for what the window does
    // not reach); no pre-seed history leaves it untouched.
    private suspend fun absorb(): AbsorbResult {
        val outcomes = trades.fetchAllBySource(TradeSource.OPENING).mapNotNull { seed -> absorbSeed(seed) }
        return AbsorbResult(
            absorbed = outcomes.count { it.status == SeedStatus.ABSORBED },
            adjusted = outcomes.count { it.status == SeedStatus.ADJUSTED },
            tickers = outcomes.map { it.ticker },
        )
    }

    private suspend fun absorbSeed(seed: Trade): SeedOutcome? {
        val covered = coveredBefore(seed)
        if (covered <= seed.absorbedQuantity) return null

        val original = seed.quantity + seed.absorbedQuantity
        val remaining = original - covered
        return if (remaining.signum() <= 0) {
            trades.delete(seed.id)
            SeedOutcome(seed.ticker, SeedStatus.ABSORBED)
        } else {
            trades.absorb(seed.id, remaining, covered)
            SeedOutcome(seed.ticker, SeedStatus.ADJUSTED)
        }
    }

    // The net quantity the imported history establishes before the seed's date. Only B3-confirmed
    // records count: a manual entry the broker never confirmed is not the history the export covers.
    private suspend fun coveredBefore(seed: Trade): BigDecimal =
        trades.fetchAllBy(seed.ticker)
            .filter { it.id != seed.id && it.isImported && it.date < seed.date }
            .fold(BigDecimal.ZERO) { quantity, trade ->
                if (trade is Trade.Buy) quantity + trade.quantity else quantity - trade.quantity
            }
}

private const val ADOPTION_INSTITUTION = "ADOPTION"

private data class AbsorbResult(val absorbed: Int, val adjusted: Int, val tickers: List<String>)

private data class SeedOutcome(val ticker: String, val status: SeedStatus)

private enum class SeedStatus {
    ABSORBED,
    ADJUSTED,
}
