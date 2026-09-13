package dev.agner.portfolio.usecase.tax.stepup

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.nextDay
import dev.agner.portfolio.usecase.commons.toMondayIfWeekend
import dev.agner.portfolio.usecase.tax.stepup.model.StepUpPlan
import dev.agner.portfolio.usecase.tax.stepup.model.StepUpSuggestion
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/** One position eligible for the step-up — already filtered to stocks with an unrealized gain and no day-trade risk. */
data class StepUpCandidate(
    val listedAssetId: Int,
    val ticker: String,
    val quantity: BigDecimal,
    val averagePrice: BigDecimal,
    val currentPrice: BigDecimal,
)

/**
 * Pure: greedily fills the month's remaining sale-exemption ceiling with the candidates that
 * realize the most exempt gain per real sold — i.e. sorted by unit gain ratio, not by total gain,
 * since the constraint is on notional sold, not on gain. Whole shares only (can't sell a
 * fraction), so this is an integer knapsack approximated greedily rather than solved exactly —
 * good enough for a handful of tickers, and not claimed optimal.
 */
@Component
class StepUpPlanner {

    fun plan(candidates: List<StepUpCandidate>, remainingCeiling: BigDecimal, today: LocalDate): StepUpPlan {
        val ordered = candidates
            .filter { it.currentPrice > it.averagePrice && it.quantity > BigDecimal.ZERO }
            .sortedByDescending { unitGainRatio(it) }

        var budget = remainingCeiling.max(BigDecimal.ZERO)
        val suggestions = mutableListOf<StepUpSuggestion>()
        val rebuyDate = today.nextDay().toMondayIfWeekend()

        for (candidate in ordered) {
            // <= 0, not isZero(): notional is rounded to two places, so a whole-share fill can
            // overshoot the budget by cents and leave it slightly negative. Guarding on zero alone
            // would then let a negative maxByBudget through as a negative suggested quantity.
            if (budget <= BigDecimal.ZERO) continue

            val maxByBudget = budget.divide(candidate.currentPrice, 0, RoundingMode.DOWN)
            val quantity = candidate.quantity.min(maxByBudget)
            if (quantity <= BigDecimal.ZERO) continue

            val notional = (quantity * candidate.currentPrice).defaultScale()
            val realizedGain = (quantity * (candidate.currentPrice - candidate.averagePrice)).defaultScale()

            suggestions += StepUpSuggestion(
                listedAssetId = candidate.listedAssetId,
                ticker = candidate.ticker,
                quantity = quantity,
                notional = notional,
                realizedGain = realizedGain,
                rebuyDate = rebuyDate,
            )
            budget -= notional
        }

        return StepUpPlan(
            suggestions = suggestions,
            totalRealizedGain = suggestions.sumOf { it.realizedGain }.defaultScale(),
            remainingCeilingAfter = budget.max(BigDecimal.ZERO).defaultScale(),
        )
    }

    private fun unitGainRatio(candidate: StepUpCandidate): BigDecimal =
        (candidate.currentPrice - candidate.averagePrice).divide(candidate.currentPrice, 8, RoundingMode.HALF_EVEN)
}
