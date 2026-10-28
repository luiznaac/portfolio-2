package dev.agner.portfolio.usecase.history

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.history.model.BackfillReport
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.GapService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.CustodyPosition
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapCreation
import dev.agner.portfolio.usecase.listedasset.model.OpeningSeedCreation
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.model.isImported
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.quotes.QuoteService
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

// The history backfill: it turns the adoption seeds and the custody cross-check into a ledger the
// series can be replayed from. Absorb retires each OPENING seed the imported history now covers;
// reconcile retires a GAP a true trade has replaced; detect records a GAP for a custody delta still
// unexplained. Every touched ticker's projection is replayed once, so Position, average price and
// the GAP replay agree before the run returns.
@Service
class BackfillService(
    private val trades: ITradeRepository,
    private val positions: PositionService,
    private val gaps: IGapRepository,
    private val gapService: GapService,
    private val corporateActions: ICorporateActionRepository,
    private val institutions: InstitutionService,
    private val quotes: QuoteService,
    private val transaction: ITransactionTemplate,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val log = logger()

    // Absorbing and detecting share one transaction: a run either leaves the ledger and its
    // projections consistent or changes nothing. `custody` is the end-of-window Posição export;
    // an empty list runs the seed absorption alone.
    suspend fun backfill(custody: List<CustodyPosition>): BackfillReport = transaction.execute {
        val absorbed = absorb()
        val replaced = gapService.reconcileAll()

        // The projection the delta is measured against must already include the open GAPs, so the
        // replay happens before detection rather than after it.
        absorbed.tickers.forEach { positions.reproject(it) }

        val created = detectGaps(custody)
        val touched = absorbed.tickers + created.map { it.ticker }
        touched.forEach { positions.reproject(it) }

        BackfillReport(
            absorbedSeeds = absorbed.absorbed,
            adjustedSeeds = absorbed.adjusted,
            gapsCreated = created.size,
            gapsReplaced = replaced,
            positionsReprojected = touched.toSet().size,
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

    // Custody minus ledger, per ticker: what the ledger holds but no record explains becomes a GAP.
    // An upward delta is a missing buy, priced at the current market close; a downward delta is a
    // missing sale whose proceeds are unknown, so it stays unpriced and its realized result pending.
    private suspend fun detectGaps(custody: List<CustodyPosition>): List<Gap> {
        if (custody.isEmpty()) return emptyList()

        val ledger = positions.fetchAll().associateBy { it.ticker }
        val tradeWatermark = trades.fetchAll().maxOfOrNull { it.id } ?: 0
        val actionWatermark = corporateActions.fetchAll().maxOfOrNull { it.id } ?: 0
        val custodyByTicker = custody
            .groupBy { it.ticker.normalizedTicker() }
            .mapValues { (_, rows) -> rows.map { it.quantity }.fold(BigDecimal.ZERO, BigDecimal::add) }

        return (ledger.keys + custodyByTicker.keys).sorted().mapNotNull { ticker ->
            val delta = (custodyByTicker[ticker] ?: BigDecimal.ZERO) - (ledger[ticker]?.quantity ?: BigDecimal.ZERO)
            when {
                delta.signum() > 0 -> upwardGap(
                    ticker = ticker,
                    quantity = delta,
                    fallbackPrice = ledger[ticker]?.averagePrice,
                    tradeWatermark = tradeWatermark,
                    actionWatermark = actionWatermark,
                )
                delta.signum() < 0 -> downwardGap(ticker, delta.negate(), tradeWatermark, actionWatermark)
                else -> null
            }
        }
    }

    private suspend fun upwardGap(
        ticker: String,
        quantity: BigDecimal,
        fallbackPrice: BigDecimal?,
        tradeWatermark: Int,
        actionWatermark: Int,
    ): Gap? {
        // The placeholder is a market price (D9: Preço de Fechamento). With no close and no ledger
        // average there is no honest cost: leave the delta unexplained rather than fabricating zero.
        val price = quotes.latestClose(ticker) ?: fallbackPrice
        if (price == null || price.signum() == 0) {
            log.warn("No market price for {} — leaving the custody delta open instead of pricing it at zero", ticker)
            return null
        }

        return gaps.save(
            GapCreation.MissingBuy(
                ticker = ticker,
                date = LocalDate.today(clock),
                quantity = quantity,
                unitPrice = price,
                tradeWatermark = tradeWatermark,
                actionWatermark = actionWatermark,
            ),
        )
    }

    private suspend fun downwardGap(
        ticker: String,
        quantity: BigDecimal,
        tradeWatermark: Int,
        actionWatermark: Int,
    ) = gaps.save(
        GapCreation.MissingSale(
            ticker = ticker,
            date = LocalDate.today(clock),
            quantity = quantity,
            tradeWatermark = tradeWatermark,
            actionWatermark = actionWatermark,
        ),
    )
}

private const val ADOPTION_INSTITUTION = "ADOPTION"

private data class AbsorbResult(val absorbed: Int, val adjusted: Int, val tickers: List<String>)

private data class SeedOutcome(val ticker: String, val status: SeedStatus)

private enum class SeedStatus {
    ABSORBED,
    ADJUSTED,
}
