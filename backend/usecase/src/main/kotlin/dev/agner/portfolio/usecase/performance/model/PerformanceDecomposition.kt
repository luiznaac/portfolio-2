package dev.agner.portfolio.usecase.performance.model

import java.math.BigDecimal

// One month's return decomposition: what the portfolio gained after the external money and the
// income the month received are netted out. `flow` is a strategy slice's bookkeeping attribution
// change (a transfer or re-attribution) valued at the month's close — it moved units into or out of
// the slice without being performance, so the return nets it too. A null `valueStart` (the first
// snapshot in the series) has no decomposition — there is no prior point to measure from.
data class PerformanceDecomposition(
    val valueStart: BigDecimal?,
    val valueEnd: BigDecimal,
    val netAportes: BigDecimal,
    val netIncome: BigDecimal,
    val flow: BigDecimal = BigDecimal.ZERO,
    val returnPercentage: BigDecimal?,
)
