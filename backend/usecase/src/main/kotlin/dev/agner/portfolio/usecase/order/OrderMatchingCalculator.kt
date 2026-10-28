package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.isImported
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.SuggestedOrderCreation
import dev.agner.portfolio.usecase.order.model.lifecycleStatus
import dev.agner.portfolio.usecase.order.model.matchKey

// The lifecycle match: every candidate is matched against the open month's executed trades by
// ticker + side. The cumulative quantity of those trades decides the state — reaching the
// suggestion marks it EXECUTED, and a matched B3-imported execution (directly, or a manual trade the
// import confirmed) marks it IMPORTED. Trades no suggestion asked for are the month's extras.
data class OrderMatching(
    val orders: List<SuggestedOrderCreation>,
    val unplannedTrades: List<Trade>,
)

object OrderMatchingCalculator {

    fun match(candidates: List<OrderCandidate>, trades: List<Trade>): OrderMatching {
        // A ticker's BUY and SELL are distinct matches: the side is part of the key, never inferred
        // from the suggestion's quantity sign.
        val tradesByKey = trades.groupBy { it.matchKey }
        val orders = candidates.map { candidate ->
            val matched = tradesByKey[candidate.matchKey].orEmpty()
                .sortedWith(compareBy({ it.date }, { it.id }))
            toOrder(candidate = candidate, matched = matched)
        }
        val plannedKeys = candidates.map { it.matchKey }.toSet()
        val unplanned = trades.filter { it.matchKey !in plannedKeys }

        return OrderMatching(orders = orders, unplannedTrades = unplanned)
    }

    private fun toOrder(candidate: OrderCandidate, matched: List<Trade>): SuggestedOrderCreation {
        val executed = matched.sumOf { it.quantity }.defaultScale()
        val imported = matched.any { it.isImported }

        return SuggestedOrderCreation(
            ticker = candidate.ticker,
            side = candidate.side,
            quantity = candidate.quantity,
            suggestedPrice = candidate.price,
            executedQuantity = executed,
            status = lifecycleStatus(
                quantity = candidate.quantity,
                executedQuantity = executed,
                imported = imported,
            ),
            matchedTradeId = matched.firstOrNull()?.id,
        )
    }
}
