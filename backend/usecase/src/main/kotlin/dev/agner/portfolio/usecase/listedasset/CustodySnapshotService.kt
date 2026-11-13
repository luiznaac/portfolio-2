package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.model.CustodyPosition
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapCreation
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.quotes.QuoteService
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

// The custody snapshot seam: custody minus the ledger becomes a GAP per ticker, and every ticker a
// gap explains is replayed so the stored projection equals the snapshot. Shared by the backfill's
// cross-check and the import assistant's POSITIONS apply — the two entry points that receive a B3
// Posição export. A divergence is recorded, never a blocker: pricing or replacing the GAP is
// reconciliation, a separate act. The caller owns the transaction; this service only reads, writes
// the gaps and replays.
@Service
class CustodySnapshotService(
    private val positions: PositionService,
    private val gaps: IGapRepository,
    private val trades: ITradeRepository,
    private val corporateActions: ICorporateActionRepository,
    private val quotes: QuoteService,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val log = logger()

    // Records the snapshot: each delta becomes a GAP and every ticker the new gaps explain is
    // replayed, so the position the owner reads afterwards is the custody quantity. A ticker the
    // snapshot cannot explain is left untouched rather than replayed into a fabricated zero.
    suspend fun apply(custody: List<CustodyPosition>) {
        openGaps(custody).map { it.ticker }.distinct().forEach { positions.reproject(it) }
    }

    // The delta detection and its GAP writes: custody and ledger are compared over the union of
    // both sides, so a ticker the ledger holds but custody does not is drift too. An upward delta
    // is a missing buy priced at the market close (or the ledger average); a downward delta is a
    // missing sale whose proceeds are unknown, so it stays unpriced and its realized result
    // pending. A delta with no honest price stays unexplained — never a fabricated zero.
    suspend fun openGaps(custody: List<CustodyPosition>): List<Gap> {
        if (custody.isEmpty()) return emptyList()

        val ledger = positions.fetchAll().associateBy { it.ticker }
        val tradeWatermark = trades.fetchAll().maxOfOrNull { it.id } ?: 0
        val actionWatermark = corporateActions.fetchAll().maxOfOrNull { it.id } ?: 0
        val custodyByTicker = custody
            .groupBy { it.ticker.normalizedTicker() }
            .mapValues { (_, rows) -> rows.map { it.quantity }.fold(BigDecimal.ZERO, BigDecimal::add) }

        return (ledger.keys + custodyByTicker.keys).sorted().mapNotNull { ticker ->
            val delta = (custodyByTicker[ticker] ?: BigDecimal.ZERO) -
                (ledger[ticker]?.quantity ?: BigDecimal.ZERO)
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
