package dev.agner.portfolio.usecase.brokeragenote

import dev.agner.portfolio.usecase.brokeragenote.model.ImportPreview
import dev.agner.portfolio.usecase.brokeragenote.model.ImportedTrade
import dev.agner.portfolio.usecase.brokeragenote.model.ImportedTradeConfirmation
import dev.agner.portfolio.usecase.brokeragenote.parser.BrokerageNoteParseException
import dev.agner.portfolio.usecase.brokeragenote.parser.IBrokerageNoteParser
import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.OrderKind
import dev.agner.portfolio.usecase.trade.TradeService
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.model.TradeCreation
import dev.agner.portfolio.usecase.trade.model.TradeSide
import kotlinx.coroutines.CancellationException
import org.springframework.stereotype.Service
import java.io.IOException

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
        // transfer proposals as a side effect of being looked at. matchesPlan is informational and
        // never blocks confirmation, so a quote-gateway hiccup while computing the plan must not
        // turn a fully parseable statement into a failed preview: degrade every row to "doesn't
        // match" instead. Cancellation is not a hiccup — it must keep propagating.
        val plannedKindByTicker = try {
            orderPlanService.computePlan().orders.associate { it.ticker to it.kind }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException) {
            logger().warn("Could not compute order plan for preview; matchesPlan degraded to false", e)
            emptyMap()
        } catch (e: IOException) {
            logger().warn("Could not compute order plan for preview; matchesPlan degraded to false", e)
            emptyMap()
        }

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
    suspend fun confirm(confirmations: List<ImportedTradeConfirmation>): List<Trade> =
        // The batch is one operation from the ledger's point of view: a failure on row k must roll
        // back rows 1..k-1, keeping the two-step flow's promise that the ledger only changes on a
        // successful confirmation.
        transaction.execute {
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
        OrderKind.SELL, OrderKind.FULL_EXIT -> side == TradeSide.SELL
        null -> false
    }
}
