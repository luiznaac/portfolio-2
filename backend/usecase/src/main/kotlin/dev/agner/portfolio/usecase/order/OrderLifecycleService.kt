package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.AttributionService
import dev.agner.portfolio.usecase.listedasset.InvalidTransferException
import dev.agner.portfolio.usecase.listedasset.model.AttributionTransferCreation
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.isImported
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.order.model.InvalidOrderMatchException
import dev.agner.portfolio.usecase.order.model.OrderList
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.order.model.SuggestedOrder
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalCreation
import dev.agner.portfolio.usecase.order.model.TransferStatus
import dev.agner.portfolio.usecase.order.model.UnplannedTrade
import dev.agner.portfolio.usecase.order.model.lifecycleStatus
import dev.agner.portfolio.usecase.order.model.matchKey
import dev.agner.portfolio.usecase.order.repository.ISuggestedOrderRepository
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.springframework.stereotype.Service
import java.math.BigDecimal

// The order lifecycle's write side. `refresh()` recomputes the plan and persists it: the month's
// suggestions (matched against the trades already executed) and the Attribution Transfer inbox
// (auto-approving the moves below R$500). Reads never recompute — `fetch()` returns the persisted
// month unchanged — so a decision made in the inbox is not silently undone by opening the screen.
// Every multi-table write runs inside the transaction seam.
@Service
class OrderLifecycleService(
    private val months: MonthService,
    private val plans: OrderPlanService,
    private val trades: ITradeRepository,
    private val suggestions: ISuggestedOrderRepository,
    private val transfers: ITransferProposalRepository,
    private val attributionService: AttributionService,
    private val strategies: IStrategyRepository,
    private val transaction: ITransactionTemplate,
) {

    // Null when no month is open — the edge maps that to the 404 the current-month reads promise.
    suspend fun fetch(): OrderList? {
        val month = months.fetchCurrent() ?: return null
        val monthTrades = monthTrades(month.month.period)
        val orders = suggestions.fetchBy(month.month.id)
        val planned = orders.map { it.matchKey }.toSet()
        val unplanned = monthTrades.filter { it.matchKey !in planned }.map { it.toUnplanned() }

        return OrderList(
            period = month.month.period,
            orders = orders,
            transfers = transfers.fetchBy(month.month.id),
            unplannedTrades = unplanned,
        )
    }

    suspend fun refresh(): OrderList? {
        val month = months.fetchCurrent() ?: return null
        val plan = plans.computePlan(OrderPlanPhase.FULL) ?: return null
        val monthTrades = monthTrades(month.month.period)
        val matching = OrderMatchingCalculator.match(candidates = plan.candidates, trades = monthTrades)

        return transaction.execute {
            val orders = suggestions.replaceAll(monthId = month.month.id, orders = matching.orders)
            refreshTransfers(month = month.month, plan = plan)

            OrderList(
                period = month.month.period,
                orders = orders,
                transfers = transfers.fetchBy(month.month.id),
                unplannedTrades = matching.unplannedTrades.map { it.toUnplanned() },
            )
        }
    }

    // Approving moves the bookkeeping and records the decision. A decided proposal is returned
    // unchanged, so a double click can never move the units twice.
    suspend fun approveTransfer(id: Int): TransferProposal? = transaction.execute {
        val proposal = transfers.fetchById(id) ?: return@execute null
        if (proposal.status != TransferStatus.PENDING) {
            proposal
        } else {
            applyTransfer(proposal)
            transfers.decide(id, TransferStatus.APPROVED)
        }
    }

    suspend fun rejectTransfer(id: Int): TransferProposal? = transaction.execute {
        val proposal = transfers.fetchById(id) ?: return@execute null
        if (proposal.status != TransferStatus.PENDING) {
            proposal
        } else {
            transfers.decide(id, TransferStatus.REJECTED)
        }
    }

    // A human correction of one match. Nulling the trade clears the override and returns the order
    // to SUGGESTED; a trade must belong to the order's ticker and ledger side, or it cannot be this
    // order's execution.
    suspend fun overrideMatch(orderId: Int, tradeId: Int?): SuggestedOrder? = transaction.execute {
        val order = suggestions.fetchById(orderId) ?: return@execute null
        if (tradeId == null) {
            return@execute suggestions.updateMatch(
                orderId,
                null,
                BigDecimal.ZERO,
                SuggestedOrderStatus.SUGGESTED,
            )
        }

        val trade = trades.fetchById(tradeId)
            ?: throw InvalidOrderMatchException("trade $tradeId does not exist")
        if (trade.matchKey != order.matchKey) {
            throw InvalidOrderMatchException(
                "trade $tradeId is ${trade.ticker} ${trade.side}, but order $orderId is ${order.ticker} ${order.side}",
            )
        }

        suggestions.updateMatch(
            id = orderId,
            matchedTradeId = trade.id,
            executedQuantity = trade.quantity,
            status = lifecycleStatus(
                quantity = order.quantity,
                executedQuantity = trade.quantity,
                imported = trade.isImported,
            ),
        )
    }

    // The inbox refresh keeps the month's decided trail: a rejected proposal is not proposed again
    // (the user already said no), and the pending rows are replaced by the fresh computation. The
    // transfers themselves are the full plan's attribution pass — this method only resolves their
    // strategy names to ids, records the decision and applies the auto-approved moves. A bucket as
    // the source never auto-approves: the engine may not move legacy units on its own, whatever the
    // R$ amount, so only a report-to-report move is decided by the threshold.
    private suspend fun refreshTransfers(month: Month, plan: OrderPlan) {
        val rejected = transfers.fetchBy(month.id)
            .filter { it.status == TransferStatus.REJECTED }
            .map { ProposalKey(it.ticker, it.fromStrategyId, it.toStrategyId) }
            .toSet()
        val catalog = strategies.fetchAll()
        val idsByName = catalog.associate { it.name to it.id }
        val manualIds = catalog.filter { it.kind == StrategyKind.MANUAL }.map { it.id }.toSet()

        transfers.deletePending(month.id)
        for (transfer in plan.transfers) {
            // A target's strategy name is only proposable when the strategy is registered: the
            // transfer needs an attribution slice to move, and that slice is keyed by strategy id.
            val fromStrategyId = idsByName[transfer.fromStrategy] ?: continue
            val toStrategyId = idsByName[transfer.toStrategy] ?: continue
            if (ProposalKey(transfer.ticker, fromStrategyId, toStrategyId) in rejected) continue

            // An unpriced transfer has no R$ value, so the threshold cannot decide it: it waits.
            // A bucket-sourced move always waits, whatever the amount.
            val autoApproved = fromStrategyId !in manualIds &&
                transfer.amount?.let { it < AUTO_APPROVAL_THRESHOLD } == true
            transfers.save(
                month.id,
                TransferProposalCreation(
                    ticker = transfer.ticker,
                    fromStrategyId = fromStrategyId,
                    toStrategyId = toStrategyId,
                    quantity = transfer.quantity,
                    amount = transfer.amount,
                    autoApproved = autoApproved,
                    status = if (autoApproved) TransferStatus.APPROVED else TransferStatus.PENDING,
                ),
            )
            if (autoApproved) {
                applyTransfer(
                    ticker = transfer.ticker,
                    fromStrategyId = fromStrategyId,
                    toStrategyId = toStrategyId,
                    quantity = transfer.quantity,
                )
            }
        }
    }

    private suspend fun applyTransfer(proposal: TransferProposal) = applyTransfer(
        ticker = proposal.ticker,
        fromStrategyId = proposal.fromStrategyId,
        toStrategyId = proposal.toStrategyId,
        quantity = proposal.quantity,
    )

    private suspend fun applyTransfer(
        ticker: String,
        fromStrategyId: Int,
        toStrategyId: Int,
        quantity: BigDecimal,
    ) {
        attributionService.transfer(
            ticker,
            AttributionTransferCreation(
                fromStrategyId = fromStrategyId,
                toStrategyId = toStrategyId,
                quantity = quantity,
            ),
        ) ?: throw InvalidTransferException(
            "$ticker can no longer move $quantity from strategy $fromStrategyId to strategy $toStrategyId",
        )
    }

    private suspend fun monthTrades(period: YearMonth): List<Trade> =
        trades.fetchAll().filter { it.date.inPeriod(period) }

    private fun LocalDate.inPeriod(period: YearMonth): Boolean =
        year == period.year && month == period.month

    companion object {
        // Below this R$ amount a transfer decides itself; at the threshold a human rules.
        val AUTO_APPROVAL_THRESHOLD: BigDecimal = BigDecimal("500")
    }
}

// A proposal's identity across refreshes: the same move between the same slices is the same
// suggestion even if its quantity was recomputed.
private data class ProposalKey(
    val ticker: String,
    val fromStrategyId: Int,
    val toStrategyId: Int,
)

private fun Trade.toUnplanned() = UnplannedTrade(
    id = id,
    ticker = ticker,
    side = side,
    quantity = quantity,
)
