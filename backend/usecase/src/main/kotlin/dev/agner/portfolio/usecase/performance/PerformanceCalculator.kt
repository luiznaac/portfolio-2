package dev.agner.portfolio.usecase.performance

import dev.agner.portfolio.usecase.performance.model.PerformanceDecomposition
import java.math.BigDecimal
import java.math.RoundingMode

// Monthly decomposition: return = (value_end - value_start - net aportes - net income) / value_start.
// The formula is a percentage; the starting value is the prior snapshot's value, so the first
// snapshot in a series has no decomposition.
object PerformanceCalculator {

    fun decompose(
        valueStart: BigDecimal?,
        valueEnd: BigDecimal,
        netAportes: BigDecimal,
        netIncome: BigDecimal,
    ): PerformanceDecomposition = PerformanceDecomposition(
        valueStart = valueStart,
        valueEnd = valueEnd,
        netAportes = netAportes,
        netIncome = netIncome,
        returnPercentage = valueStart
            ?.takeIf { it.signum() != 0 }
            ?.let { start ->
                (valueEnd - start - netAportes - netIncome)
                    .multiply(HUNDRED)
                    .divide(start, PERFORMANCE_SCALE, RoundingMode.HALF_UP)
            },
    )
}
