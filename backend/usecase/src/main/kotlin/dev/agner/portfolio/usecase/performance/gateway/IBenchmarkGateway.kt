package dev.agner.portfolio.usecase.performance.gateway

import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkObservation
import kotlinx.datetime.LocalDate

// The benchmark series source (BCB SGS in the gateway adapter). A series is fetched for a closed
// interval and returned oldest first; an absent series is an empty list.
interface IBenchmarkGateway {

    suspend fun fetchSeries(
        index: BenchmarkIndex,
        from: LocalDate,
        to: LocalDate,
    ): List<BenchmarkObservation>
}
