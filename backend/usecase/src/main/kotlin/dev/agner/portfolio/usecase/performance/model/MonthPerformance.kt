package dev.agner.portfolio.usecase.performance.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkBlend
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
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
// ledger) and the benchmark its policy maps to. The class line stays the direct formula over all its
// holdings; `strategies` is the realised-return slice per Strategy (the Manual Strategy included) and
// `outside` is the read-only remainder, so the class line equals its slices plus that row. Renda Fixa
// carries neither — it has no Strategy and is never "outside" one.
data class ClassPerformance(
    val assetClass: AssetClass,
    val decomposition: PerformanceDecomposition,
    val benchmarkReturn: BigDecimal?,
    val strategies: List<StrategyPerformance> = emptyList(),
    val outside: PerformanceDecomposition? = null,
)

// One Strategy's realised return for the month. There is no plan-vs-actual comparison here: the slice
// is measured on its own attributed units, its share of the class aportes and income, and its
// bookkeeping flow netted out. A Ticker shared with another slice splits by quantity.
data class StrategyPerformance(
    val strategyId: Int,
    val strategy: String,
    val kind: StrategyKind,
    val decomposition: PerformanceDecomposition,
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
