package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.isZero
import org.springframework.stereotype.Component
import java.math.BigDecimal

/** One pure match, before any persisted lifecycle — see [dev.agner.portfolio.usecase.order.model.TransferProposal] for the persisted shape OrderPlanService reconciles this against. */
data class TransferMatch(
    val listedAssetId: Int,
    val ticker: String,
    val fromStrategyId: Int,
    val toStrategyId: Int,
    val quantity: BigDecimal,
)

/**
 * Turns one ticker's per-strategy deltas into the smallest set of attribution transfers that would
 * leave every strategy as close to its ideal as the ticker's total custody allows.
 *
 * Pure and stateless: no I/O, no clock, no repository. Everything it needs is in the arguments, so
 * it is unit-testable in isolation — see [OrderPlanService], which owns the fetching.
 *
 * ### Where it sits in the plan
 *
 * Moving attribution between two strategies costs nothing: no brokerage, no tax, and it does not
 * touch the sale-exemption ceiling, unlike selling from one strategy and buying back for the
 * other. So transfers are always the cheaper lever and are tried before any trade is proposed.
 *
 * ```mermaid
 * flowchart LR
 *     A["deltas per strategy<br/>(+ = holds too much, − = holds too little)"] --> B{excess a<br/>nd shortage<br/>both non-empty?}
 *     B -- no --> C["no suggestion<br/>(nothing to move)"]
 *     B -- yes --> D["move min(excess, |shortage|)<br/>from the largest holder of excess<br/>to the largest holder of shortage"]
 *     D --> E{one side<br/>exhausted?}
 *     E -- no --> D
 *     E -- yes --> F["transfers that flatten<br/>every delta they cover"]
 * ```
 *
 * ### Invariants
 *
 * - **The net total never moves.** Σ deltas is unchanged by any suggestion, because every transfer
 *   is zero-sum between two strategies. The plan's net order for the ticker is decided by custody
 *   against the sum of ideals, irrespective of how attribution gets shuffled; this class only
 *   reduces how much of that net has to become a real trade.
 * - **Shortage is not created or destroyed.** What is taken from one strategy is added to the
 *   other, so a suggested transfer can always be applied as two opposite attribution movements
 *   (see `OrderController`'s apply endpoint).
 * - **A suggestion is never larger than either side's own delta.** Paired quantities are
 *   `min(excess, |shortage|)`, so applying every suggestion leaves no strategy on the wrong side of
 *   its ideal — at worst, some excess and some shortage are left over and remain in the net order.
 *
 * ### Preconditions (the caller's job)
 *
 * - Deltas arrive keyed by strategy id; names are resolved by the caller when it persists the
 *   proposal, not carried on the model.
 * - Zero deltas may be present and are ignored by the two `filterValues` below.
 */
@Component
class TransferMatcher {

    /**
     * Pairs this ticker's excess against its shortage and returns the resulting matches,
     * largest magnitudes first. Returns an empty list when nothing can be moved.
     */
    fun match(
        listedAssetId: Int,
        ticker: String,
        deltasByStrategy: Map<Int, BigDecimal>,
    ): List<TransferMatch> = pairRemaining(
        listedAssetId = listedAssetId,
        ticker = ticker,
        // Largest excess first and largest shortage first: the greedy pairing then starts with the
        // meatiest pair, which tends to cover the most deltas in the fewest matches.
        excess = deltasByStrategy
            .filterValues { it > BigDecimal.ZERO }
            .toList()
            .sortedByDescending { (_, delta) -> delta },
        // Stored as a magnitude, so the pairing below never has to reason about sign again.
        shortage = deltasByStrategy
            .filterValues { it < BigDecimal.ZERO }
            .toList()
            .map { (strategyId, delta) -> strategyId to delta.abs() }
            .sortedByDescending { (_, magnitude) -> magnitude },
    )

    /**
     * Walks both magnitude-sorted queues in lockstep, consuming the smaller of the two sides at
     * each step. Tail-recursive so the accumulated matches are passed along rather than held in
     * a mutable list captured from the enclosing scope.
     */
    private tailrec fun pairRemaining(
        listedAssetId: Int,
        ticker: String,
        excess: List<Pair<Int, BigDecimal>>,
        shortage: List<Pair<Int, BigDecimal>>,
        matches: List<TransferMatch> = emptyList(),
    ): List<TransferMatch> {
        // Either side running out ends the walk: whatever is left on the other side stays in the
        // net order, which is exactly what the order already accounts for.
        if (excess.isEmpty() || shortage.isEmpty()) return matches

        val (fromStrategyId, fromRemaining) = excess.first()
        val (toStrategyId, toRemaining) = shortage.first()
        val quantity = minOf(fromRemaining, toRemaining)

        val match = TransferMatch(
            listedAssetId = listedAssetId,
            ticker = ticker,
            fromStrategyId = fromStrategyId,
            toStrategyId = toStrategyId,
            quantity = quantity,
        )

        // A fully consumed side is dropped; the other keeps its residual as its new head. The
        // `isZero` check (signum, not `==`) is what makes this safe for BigDecimal, whose equals is
        // scale-sensitive — see its doc in commons.
        val remainingExcess = excess.drop(1).let { tail ->
            val residual = fromRemaining - quantity
            if (residual.isZero()) tail else listOf(fromStrategyId to residual) + tail
        }
        val remainingShortage = shortage.drop(1).let { tail ->
            val residual = toRemaining - quantity
            if (residual.isZero()) tail else listOf(toStrategyId to residual) + tail
        }

        return pairRemaining(
            listedAssetId = listedAssetId,
            ticker = ticker,
            excess = remainingExcess,
            shortage = remainingShortage,
            matches = matches + match,
        )
    }
}
