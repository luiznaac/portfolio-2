package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.model.side
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
import org.springframework.stereotype.Service
import java.math.BigDecimal

// The GAP lifecycle: a missing record is retired when a genuinely new record arrives — a trade of the
// same shape or a quantity corporate action for the ticker — or (for a missing sale) priced by the
// owner. Both paths replay the ticker's Position, because the GAP is part of that replay.
@Service
class GapService(
    private val gaps: IGapRepository,
    private val trades: ITradeRepository,
    private val corporateActions: ICorporateActionRepository,
    private val positions: PositionService,
    private val aliases: TickerAliasResolver,
    private val transaction: ITransactionTemplate,
) {

    // The read presents the paper's canonical ticker; the stored row keeps the code the custody
    // export carried.
    suspend fun fetchAll(): List<Gap> {
        val rows = gaps.fetchAll()
        val canonicalByTicker = aliases.canonicalAll(rows.map { it.ticker })
        return rows.map { it.withCanonicalTicker(canonicalByTicker.getValue(it.ticker)) }
    }

    suspend fun reconcile(ticker: String): Boolean = transaction.execute {
        val canonical = aliases.canonical(ticker)
        val aliasSet = aliases.aliasesOf(canonical)
        val open = aliasSet.flatMap { gaps.fetchOpenBy(it) }
        if (open.isEmpty()) return@execute false

        val retired = retire(
            open,
            aliasSet.flatMap { trades.fetchAllBy(it) },
            aliasSet.flatMap { corporateActions.fetchByTicker(it) },
        )
        if (retired.isNotEmpty()) positions.reproject(canonical)
        retired.isNotEmpty()
    }

    // The backfill's whole-book pass; returns how many GAP records it retired.
    suspend fun reconcileAll(): Int = transaction.execute {
        val openGaps = gaps.fetchAll().filter { it.status == GapStatus.OPEN }
        val canonicalByTicker = aliases.canonicalAll(openGaps.map { it.ticker })
        openGaps
            .groupBy { canonicalByTicker.getValue(it.ticker) }
            .entries
            .sumOf { (canonical, open) ->
                val aliasSet = aliases.aliasesOf(canonical)
                val retired = retire(
                    open,
                    aliasSet.flatMap { trades.fetchAllBy(it) },
                    aliasSet.flatMap { corporateActions.fetchByTicker(it) },
                )
                if (retired.isNotEmpty()) positions.reproject(canonical)
                retired.size
            }
    }

    // Prices a missing record: a sale's proceeds, or the market placeholder a missing buy entered the
    // replay at. Either way the position replay follows, under the paper's canonical code.
    suspend fun price(id: Int, unitPrice: BigDecimal): Gap? = transaction.execute {
        gaps.price(id, unitPrice)?.also { positions.reproject(it.ticker) }
    }

    private fun Gap.withCanonicalTicker(canonical: String): Gap = when (this) {
        is Gap.Buy -> copy(ticker = canonical)
        is Gap.Sell -> copy(ticker = canonical)
    }

    // A record explains the delta only if it is newer than the GAP's watermark: the ledger the gap was
    // measured against already held everything older, so a pre-existing same-size trade is not the
    // missing record. The date is deliberately not part of the identity — the Posição export gives no
    // date for the delta, so the GAP is stamped with the run date.
    private suspend fun retire(
        open: List<Gap>,
        ledger: List<Trade>,
        actions: List<CorporateAction>,
    ): List<Gap> = open
        .filter { gap ->
            ledger.any { trade ->
                trade.id > gap.tradeWatermark &&
                    trade.source != TradeSource.OPENING &&
                    trade.side == gap.side &&
                    trade.quantity.compareTo(gap.quantity) == 0
            } || actions.any { action ->
                action.id > gap.actionWatermark && action.isApplied && action.kind.isQuantityEvent
            }
        }
        .onEach { gaps.markReplaced(it.id) }
}
