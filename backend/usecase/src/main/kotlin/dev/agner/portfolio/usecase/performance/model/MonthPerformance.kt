package dev.agner.portfolio.usecase.performance.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkBlend
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// One closed month's performance: the total decomposition, the annualized return since inception,
// the target-weighted benchmark and the same methodology at the class and ticker rungs.
data class MonthPerformance(
    val monthId: Int,
    val period: YearMonth,
    val decomposition: PerformanceDecomposition,
    val xirr: BigDecimal?,
    val benchmark: BenchmarkBlend,
    val classes: List<ClassPerformance>,
    val tickers: List<TickerPerformance>,
)

// A class rung: its own decomposition (target-weighted share of the month's aportes, its income
// ledger) and the benchmark its policy maps to.
data class ClassPerformance(
    val assetClass: AssetClass,
    val decomposition: PerformanceDecomposition,
    val benchmarkReturn: BigDecimal?,
)

// A ticker rung: value at both closes and the same decomposition. Aportes are not tagged per asset,
// so each ticker receives the month's net aportes in proportion to its end value.
data class TickerPerformance(
    val ticker: String,
    val assetClass: AssetClass?,
    val decomposition: PerformanceDecomposition,
    // The class benchmark the ticker's asset class maps to; null when the ticker is unclassified or
    // the class benchmark could not be priced.
    val benchmarkReturn: BigDecimal? = null,
)
