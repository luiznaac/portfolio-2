package dev.agner.portfolio.usecase.performance.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The historical series built from the immutable snapshots, oldest first. The first point has no
// decomposition (no prior value); every later point carries its month return, the annualized return
// since inception at that point, the blended benchmark return for its period, and each policy index's
// own return so the history can overlay CDI/IPCA/IBOV beside the blend.
data class PerformanceSeries(
    val months: List<PeriodPerformance>,
    val xirr: BigDecimal?,
)

data class PeriodPerformance(
    val period: YearMonth,
    val decomposition: PerformanceDecomposition,
    val xirr: BigDecimal?,
    val benchmarkReturn: BigDecimal?,
    val benchmarks: List<PeriodBenchmark> = emptyList(),
    // Each class's frozen value at the close and, after the first snapshot, at the prior close — the
    // per-class value series the history screen plots across months.
    val classes: List<PeriodClass> = emptyList(),
)

// One policy index's return for the period. Null when the SGS series could not price it, so the
// overlay leaves that point out instead of reading a data gap as zero.
data class PeriodBenchmark(
    val index: BenchmarkIndex,
    val returnPercentage: BigDecimal?,
)

// One class's frozen value at the month's close.
data class PeriodClass(
    val assetClass: AssetClass,
    val valueEnd: BigDecimal,
)
