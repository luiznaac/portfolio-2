package dev.agner.portfolio.usecase.performance

import dev.agner.portfolio.usecase.performance.model.PerformanceDecomposition
import java.math.BigDecimal
import java.math.RoundingMode

// Monthly decomposition: return = (value_end - value_start - net aportes - net income - flow) /
// value_start. The formula is a percentage; the starting value is the prior snapshot's value, so the
// first snapshot in a series has no decomposition. `flow` is the bookkeeping attribution change a
// strategy slice carries: it moved units in or out without being performance, so it is netted like
// an aporte. Trade-driven value changes are not a flow — they stay in the slice's return.
object PerformanceCalculator {

    fun decompose(
        valueStart: BigDecimal?,
        valueEnd: BigDecimal,
        netAportes: BigDecimal,
        netIncome: BigDecimal,
        flow: BigDecimal = BigDecimal.ZERO,
    ): PerformanceDecomposition = PerformanceDecomposition(
        valueStart = valueStart,
        valueEnd = valueEnd,
        netAportes = netAportes,
        netIncome = netIncome,
        flow = flow,
        returnPercentage = valueStart
            ?.takeIf { it.signum() != 0 }
            ?.let { start ->
                (valueEnd - start - netAportes - netIncome - flow)
                    .multiply(HUNDRED)
                    .divide(start, PERFORMANCE_SCALE, RoundingMode.HALF_UP)
            },
    )
}
