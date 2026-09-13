package dev.agner.portfolio.usecase.monthlyclose.model

import dev.agner.portfolio.usecase.allocation.model.AssetClass
import java.math.BigDecimal

/**
 * One AssetClass whose current weight has drifted more than the threshold away from its ideal,
 * in percentage points.
 */
data class DriftAlert(
    val assetClass: AssetClass,
    val idealWeight: BigDecimal,
    val currentWeight: BigDecimal,
    val driftPP: BigDecimal,
)
