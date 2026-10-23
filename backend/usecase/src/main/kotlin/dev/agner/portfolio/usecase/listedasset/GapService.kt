package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
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
    private val transaction: ITransactionTemplate,
) {

    suspend fun fetchAll(): List<Gap> = gaps.fetchAll()

    suspend fun reconcile(ticker: String): Boolean = transaction.execute {
        val normalized = ticker.normalizedTicker()
        val open = gaps.fetchOpenBy(normalized)
        if (open.isEmpty()) return@execute false

        val retired = retire(open, trades.fetchAllBy(normalized), corporateActions.fetchByTicker(normalized))
        if (retired.isNotEmpty()) positions.reproject(normalized)
        retired.isNotEmpty()
    }

    // The backfill's whole-book pass; returns how many GAP records it retired.
    suspend fun reconcileAll(): Int = transaction.execute {
        gaps.fetchAll()
            .filter { it.status == GapStatus.OPEN }
            .groupBy { it.ticker }
            .entries
            .sumOf { (ticker, open) ->
                val retired = retire(open, trades.fetchAllBy(ticker), corporateActions.fetchByTicker(ticker))
                if (retired.isNotEmpty()) positions.reproject(ticker)
                retired.size
            }
    }

    // Prices a missing record: a sale's proceeds, or the market placeholder a missing buy entered the
    // replay at. Either way the position replay follows.
    suspend fun price(id: Int, unitPrice: BigDecimal): Gap? = transaction.execute {
        gaps.price(id, unitPrice)?.also { positions.reproject(it.ticker) }
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
