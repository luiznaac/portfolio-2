package dev.agner.portfolio.usecase.performance.benchmark

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// One stored point of a benchmark series. The interpretation of `level` is the index's kind: a daily
// rate in percent (CDI), a monthly variation in percent (IPCA), or an index level in points (IBOV).
data class BenchmarkClose(
    val index: BenchmarkIndex,
    val date: LocalDate,
    val level: BigDecimal,
    val source: BenchmarkSource,
)
