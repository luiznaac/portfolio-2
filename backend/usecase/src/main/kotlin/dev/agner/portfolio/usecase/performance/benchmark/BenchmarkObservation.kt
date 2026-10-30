package dev.agner.portfolio.usecase.performance.benchmark

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// One point of a benchmark series as a provider publishes it. The interpretation of `value` is the
// index's kind: a daily rate in percent (CDI), a monthly variation in percent (IPCA), or an index
// level in points (IBOV).
data class BenchmarkObservation(val date: LocalDate, val value: BigDecimal)

// The provider a stored point came from, kept so a refetch can record where its data originated.
enum class BenchmarkSource {
    SGS,
    BRAPI,
    YAHOO,
}

// The series the policy's benchmark lines read. The SGS code is BCB's own (CDI 12, IPCA 433,
// IBOV 7) and is read by nothing but the SGS adapter; IBOV is fetched from its own providers, so
// its code is not used.
enum class BenchmarkIndex(val sgsCode: Int, val kind: BenchmarkKind) {
    CDI(12, BenchmarkKind.DAILY_RATE),
    IPCA(433, BenchmarkKind.MONTHLY_VARIATION),
    IBOV(7, BenchmarkKind.INDEX_LEVEL),
}

enum class BenchmarkKind {
    DAILY_RATE,
    MONTHLY_VARIATION,
    INDEX_LEVEL,
}
