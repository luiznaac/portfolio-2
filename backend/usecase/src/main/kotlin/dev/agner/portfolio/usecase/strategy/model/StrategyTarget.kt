package dev.agner.portfolio.usecase.strategy.model

import java.math.BigDecimal

// One row of a broker model-portfolio report: rating/targetPrice only ever come from the stock
// table (page 1) — the FII table (page 2) has no equivalent columns, so both stay nullable.
data class StrategyTarget(
    val ticker: String,
    val weight: BigDecimal, // fraction (0.05 for 5%), same convention as AssetClassTarget
    val rating: String? = null,
    val targetPrice: BigDecimal? = null,
)
