package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.order.model.FundingSummary
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.ProRataSummary
import java.math.BigDecimal
import java.math.RoundingMode

// The pro-rata scaling's outcome: the plan's candidates after scaling and the arithmetic behind it.
data class ProRataOutcome(
    val candidates: List<OrderCandidate>,
    val summary: ProRataSummary,
)

// The funding cascade's last step: when the Uninvested Balance and every possible funding sell still
// leave the buys short, spend what is deployable across them in proportion to their demand (D5
// worked example D). The factor is deployable ÷ demand; each buy's quantity is floored to whole units
// under that ratio, so the rounding remainder stays unfunded instead of becoming a fractional order.
// A NEUTRO slice never reaches this pass — it emits no buy candidate.
object ProRataCalculator {

    private val ONE = BigDecimal.ONE
    private const val FACTOR_SCALE = 4

    fun compute(candidates: List<OrderCandidate>, funding: FundingSummary): ProRataOutcome {
        val buys = candidates.filter { it.side.isBuy }
        val demand = funding.buyAmount
        val deployable = (demand - funding.unfundedAmount).coerceAtLeast(BigDecimal.ZERO)

        if (demand.signum() <= 0 || funding.unfundedAmount.signum() <= 0) {
            return ProRataOutcome(
                candidates = candidates,
                summary = ProRataSummary(
                    originalBuyAmount = demand.defaultScale(),
                    factor = ONE,
                    buyAmount = demand.defaultScale(),
                    unfundedAmount = BigDecimal.ZERO,
                ),
            )
        }

        val scaledByTicker = buys.associate { it.ticker to scale(it, deployable = deployable, demand = demand) }
        val scaled = candidates.mapNotNull { candidate ->
            if (candidate.side.isBuy) scaledByTicker[candidate.ticker] else candidate
        }

        val scaledBuyAmount = scaled.filter { it.side.isBuy }
            .sumOf { it.amount ?: BigDecimal.ZERO }
            .defaultScale()

        return ProRataOutcome(
            candidates = scaled,
            summary = ProRataSummary(
                originalBuyAmount = demand.defaultScale(),
                factor = deployable.divide(demand, FACTOR_SCALE, RoundingMode.HALF_UP),
                buyAmount = scaledBuyAmount,
                unfundedAmount = (demand - scaledBuyAmount).defaultScale(),
            ),
        )
    }

    // The exact ratio (qty × deployable ÷ demand), not the rounded display factor, so the floor is
    // deterministic. A buy that would fall under one unit is dropped rather than emitted as zero.
    private fun scale(candidate: OrderCandidate, deployable: BigDecimal, demand: BigDecimal): OrderCandidate? {
        val quantity = candidate.quantity
            .multiply(deployable)
            .divide(demand, 0, RoundingMode.FLOOR)
        return if (quantity.signum() <= 0) null else candidate.withQuantity(quantity)
    }
}
