package dev.agner.portfolio.usecase.brokeragenote

import dev.agner.portfolio.usecase.brokeragenote.model.ImportPreview
import dev.agner.portfolio.usecase.brokeragenote.model.ImportedTrade
import dev.agner.portfolio.usecase.brokeragenote.model.ImportedTradeConfirmation
import dev.agner.portfolio.usecase.brokeragenote.parser.BrokerageNoteParseException
import dev.agner.portfolio.usecase.brokeragenote.parser.IBrokerageNoteParser
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.OrderKind
import dev.agner.portfolio.usecase.trade.TradeService
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.model.TradeCreation
import dev.agner.portfolio.usecase.trade.model.TradeSide
import org.springframework.stereotype.Service

/**
 * Two-step import: [preview] parses the statement and reconciles it against the current
 * [dev.agner.portfolio.usecase.order.model.OrderPlan] without writing anything; only [confirm],
 * with the user-approved subset, creates real [Trade]s.
 *
 * Scoped to the trades sheet ("Negociação de Ativos") only — distributions and automatic
 * corporate-action detection from the statement text are handled elsewhere or left for later.
 */
@Service
class BrokerageNoteService(
    private val parser: IBrokerageNoteParser,
    private val listedAssetRepository: IListedAssetRepository,
    private val tradeService: TradeService,
    private val orderPlanService: OrderPlanService,
    private val transaction: ITransactionTemplate,
) {

    suspend fun preview(xlsxBytes: ByteArray): ImportPreview {
        val parsed = parser.parse(xlsxBytes)
        if (parsed.isEmpty()) {
            throw BrokerageNoteParseException("No trades found in statement")
        }

        // computePlan is the read-only path on purpose: a preview must not create or auto-apply
        // transfer proposals as a side effect of being looked at.
        val plannedKindByTicker = orderPlanService.computePlan().orders.associate { it.ticker to it.kind }

        val trades = parsed.map { row ->
            val assetId = listedAssetRepository.resolveIdByTicker(row.ticker, row.date)

            ImportedTrade(
                ticker = row.ticker,
                listedAssetId = assetId,
                date = row.date,
                side = row.side,
                quantity = row.quantity,
                price = row.price,
                notional = (row.quantity * row.price).defaultScale(),
                resolvable = assetId != null,
                matchesPlan = matchesPlan(plannedKindByTicker[row.ticker], row.side),
            )
        }

        return ImportPreview(
            trades = trades,
            unresolvedTickers = trades.filterNot { it.resolvable }.map { it.ticker }.distinct(),
        )
    }

    /** All or nothing: a half-imported statement is worse than a rejected one. */
    suspend fun confirm(confirmations: List<ImportedTradeConfirmation>): List<Trade> = transaction.execute {
        confirmations.map { confirmation ->
            val assetId = listedAssetRepository.resolveIdByTicker(confirmation.ticker, confirmation.date)
                ?: throw BrokerageNoteParseException("Unresolved ticker ${confirmation.ticker} — cannot confirm")

            tradeService.create(
                assetId,
                TradeCreation(
                    date = confirmation.date,
                    side = confirmation.side,
                    quantity = confirmation.quantity,
                    price = confirmation.price,
                ),
            )
        }
    }

    private fun matchesPlan(plannedKind: OrderKind?, side: TradeSide): Boolean = when (plannedKind) {
        OrderKind.BUY, OrderKind.NEW_ENTRY -> side == TradeSide.BUY
        OrderKind.SELL, OrderKind.EXIT -> side == TradeSide.SELL
        null -> false
    }
}
