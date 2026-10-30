package dev.agner.portfolio.usecase.performance.gateway

import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkObservation
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkSource
import kotlinx.datetime.LocalDate

// One provider in an index's ordered source chain. The store asks the lowest `order` first and drops
// to the next one for whatever window is still missing, so a provider that cannot serve the whole
// interval (a free plan clamps to three months) only contributes the points it does have.
interface IBenchmarkSeriesSource {

    val source: BenchmarkSource

    val order: Int

    val indexes: Set<BenchmarkIndex>

    suspend fun fetchSeries(
        index: BenchmarkIndex,
        from: LocalDate,
        to: LocalDate,
    ): List<BenchmarkObservation>
}
