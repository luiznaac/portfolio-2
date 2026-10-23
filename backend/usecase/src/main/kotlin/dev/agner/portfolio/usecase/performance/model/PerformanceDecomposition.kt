package dev.agner.portfolio.usecase.performance.model

import java.math.BigDecimal

// One month's return decomposition: what the portfolio gained after the external money and the
// income the month received are netted out. A null `valueStart` (the first snapshot in the series)
// has no decomposition — there is no prior point to measure from.
data class PerformanceDecomposition(
    val valueStart: BigDecimal?,
    val valueEnd: BigDecimal,
    val netAportes: BigDecimal,
    val netIncome: BigDecimal,
    val returnPercentage: BigDecimal?,
)
