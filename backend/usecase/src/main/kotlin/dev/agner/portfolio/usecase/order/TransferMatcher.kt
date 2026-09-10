package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.isZero
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * One pure match, before any persisted lifecycle — see
 * [dev.agner.portfolio.usecase.order.model.TransferProposal] for the stored shape
 * [OrderPlanService] reconciles this against.
 */
data class TransferMatch(
    val listedAssetId: Int,
    val ticker: String,
    val fromStrategyId: Int,
    val fromStrategyName: String,
    val toStrategyId: Int,
    val toStrategyName: String,
    val quantity: BigDecimal,
)

/**
 * Pure: given each strategy's delta (current − ideal, in shares) for one ticker, greedily pairs
 * strategies with excess (delta > 0) against strategies with shortage (delta < 0), moving
 * min(excess, |shortage|) at a time until one side runs out. Doesn't touch the net total — that's
 * fixed by custody vs. the sum of ideals regardless of how attribution gets shuffled between
 * strategies; this only reduces how much of it has to be a real trade. See OrderPlanService.
 */
@Component
class TransferMatcher {

    fun match(
        listedAssetId: Int,
        ticker: String,
        deltasByStrategy: Map<Int, BigDecimal>,
        strategyNames: Map<Int, String>,
    ): List<TransferMatch> {
        val excess = deltasByStrategy.filterValues { it > BigDecimal.ZERO }
            .toList()
            .sortedByDescending { it.second }
            .toMutableList()
        val shortage = deltasByStrategy.filterValues { it < BigDecimal.ZERO }
            .toList()
            .sortedBy { it.second }
            .map { it.first to it.second.abs() }
            .toMutableList()

        val matches = mutableListOf<TransferMatch>()
        var e = 0
        var s = 0
        while (e < excess.size && s < shortage.size) {
            val (fromId, fromRemaining) = excess[e]
            val (toId, toRemaining) = shortage[s]
            val quantity = minOf(fromRemaining, toRemaining)

            matches += TransferMatch(
                listedAssetId = listedAssetId,
                ticker = ticker,
                fromStrategyId = fromId,
                fromStrategyName = strategyNames[fromId].orEmpty(),
                toStrategyId = toId,
                toStrategyName = strategyNames[toId].orEmpty(),
                quantity = quantity,
            )

            excess[e] = fromId to (fromRemaining - quantity)
            shortage[s] = toId to (toRemaining - quantity)
            if (excess[e].second.isZero()) e++
            if (shortage[s].second.isZero()) s++
        }

        return matches
    }
}
