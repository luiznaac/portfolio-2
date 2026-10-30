package dev.agner.portfolio.usecase.performance.repository

import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkClose
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import kotlinx.datetime.LocalDate

// The stored benchmark series. `(index, date)` is unique, so an upsert of a point already on file
// replaces its level and source instead of duplicating it.
interface IBenchmarkCloseRepository {

    suspend fun fetch(index: BenchmarkIndex, from: LocalDate, to: LocalDate): List<BenchmarkClose>

    suspend fun upsertAll(closes: List<BenchmarkClose>)
}
