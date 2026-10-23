package dev.agner.portfolio.usecase.performance.model

import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The historical series built from the immutable snapshots, oldest first. The first point has no
// decomposition (no prior value); every later point carries its month return, the annualized return
// since inception at that point, and the blended benchmark return for its period.
data class PerformanceSeries(
    val months: List<PeriodPerformance>,
    val xirr: BigDecimal?,
)

data class PeriodPerformance(
    val period: YearMonth,
    val decomposition: PerformanceDecomposition,
    val xirr: BigDecimal?,
    val benchmarkReturn: BigDecimal?,
)
