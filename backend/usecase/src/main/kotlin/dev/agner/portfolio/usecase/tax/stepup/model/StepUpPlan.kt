package dev.agner.portfolio.usecase.tax.stepup.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * A candidate lot for the step-up: sell it today (realizing the exempt gain), rebuy at
 * [dev.agner.portfolio.usecase.tax.stepup.StepUpPlanner]'s suggested date to raise the average
 * price for free. Stocks only — FIIs have no sale exemption to maximize.
 */
data class StepUpSuggestion(
    val listedAssetId: Int,
    val ticker: String,
    val quantity: BigDecimal,
    val notional: BigDecimal,
    val realizedGain: BigDecimal,
    val rebuyDate: LocalDate,
)

data class StepUpPlan(
    val suggestions: List<StepUpSuggestion>,
    val totalRealizedGain: BigDecimal,
    val remainingCeilingAfter: BigDecimal,
)
