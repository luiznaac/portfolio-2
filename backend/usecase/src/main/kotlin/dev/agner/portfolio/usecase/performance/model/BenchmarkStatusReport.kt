package dev.agner.portfolio.usecase.performance.model

import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import kotlinx.datetime.LocalDateTime

// The freshness of the stored benchmark series, one row per index. `lastFetchedAt` is the moment the
// index last contributed a point from a provider — null when the index was never fetched.
data class BenchmarkStatusReport(val indexes: List<BenchmarkStatus>)

data class BenchmarkStatus(
    val index: BenchmarkIndex,
    val lastFetchedAt: LocalDateTime?,
)
