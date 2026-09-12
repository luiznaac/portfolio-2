package dev.agner.portfolio.usecase.brokeragenote

import dev.agner.portfolio.usecase.brokeragenote.model.ImportPreview
import dev.agner.portfolio.usecase.brokeragenote.model.ImportedTrade
import dev.agner.portfolio.usecase.brokeragenote.model.ImportedTradeConfirmation
import dev.agner.portfolio.usecase.brokeragenote.parser.BrokerageNoteParseException
import dev.agner.portfolio.usecase.brokeragenote.parser.IBrokerageNoteParser
import dev.agner.portfolio.usecase.brokeragenote.parser.TradeSide
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.OrderKind
import dev.agner.portfolio.usecase.trade.TradeService
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.model.TradeCreation
import org.springframework.stereotype.Service

/**
 * Two-step import: [preview] parses the statement and reconciles it against the current
 * [dev.agner.portfolio.usecase.order.model.OrderPlan] without writing anything — "nada entra no
 * livro-razão antes de você confirmar" (plan's Fase 4). Only [confirm], with the user-approved
 * subset, actually creates [Trade]s. Deliberately scoped to the "Negociação de Ativos" (trades)
 * sheet only — dividends/JCP/rendimentos and automatic corporate-action detection from the
 * statement text are left for a later pass, same simplification pattern as prior phases.
 */
@Service
class BrokerageNoteService(
    private val parser: IBrokerageNoteParser,
    private val listedAssetRepository: IListedAssetRepository,
    private val tradeService: TradeService,
    private val orderPlanService: OrderPlanService,
) {

    suspend fun preview(xlsxBytes: ByteArray): ImportPreview {
        val parsed = parser.parse(xlsxBytes)
        if (parsed.isEmpty()) {
            throw BrokerageNoteParseException("No trades found in statement")
        }

        // matchesPlan is informational and never blocks confirmation, so a quote-gateway hiccup
        // while computing the plan must not turn a fully parseable statement into a failed preview:
        // degrade every row to "doesn't match" instead. computePlan() is suspend, so runCatching
        // can't be used here.
        val plannedSideByTicker = try {
            orderPlanService.computePlan().orders.associate { it.ticker to it.kind }
        } catch (e: Exception) {
            logger().warn("Could not compute order plan for preview; matchesPlan degraded to false", e)
            emptyMap()
        }

        val trades = parsed.map { row ->
            val assetId = listedAssetRepository.resolveIdByTicker(row.ticker, row.date)
            val signedQuantity = if (row.side == TradeSide.COMPRA) row.quantity else row.quantity.negate()

            ImportedTrade(
                ticker = row.ticker,
                listedAssetId = assetId,
                date = row.date,
                quantity = signedQuantity,
                price = row.price,
                notional = (row.quantity * row.price).defaultScale(),
                resolvable = assetId != null,
                matchesPlan = matchesPlan(plannedSideByTicker[row.ticker], row.side),
            )
        }

        return ImportPreview(
            trades = trades,
            unresolvedTickers = trades.filterNot { it.resolvable }.map { it.ticker }.distinct(),
        )
    }

    suspend fun confirm(confirmations: List<ImportedTradeConfirmation>): List<Trade> =
        confirmations.map { confirmation ->
            val assetId = listedAssetRepository.resolveIdByTicker(confirmation.ticker, confirmation.date)
                ?: throw BrokerageNoteParseException("Unresolved ticker ${confirmation.ticker} — cannot confirm")

            tradeService.create(
                TradeCreation(
                    assetId = assetId,
                    date = confirmation.date,
                    quantity = confirmation.quantity,
                    price = confirmation.price,
                ),
            )
        }

    private fun matchesPlan(plannedKind: OrderKind?, side: TradeSide): Boolean = when (plannedKind) {
        OrderKind.BUY, OrderKind.NEW_ENTRY -> side == TradeSide.COMPRA
        OrderKind.SELL, OrderKind.FULL_EXIT -> side == TradeSide.VENDA
        null -> false
    }
}
