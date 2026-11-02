package dev.agner.portfolio.usecase.performance.repository

import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkClose
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

// The stored benchmark series. `(index, date)` is unique, so an upsert of a point already on file
// replaces its level and source instead of duplicating it.
interface IBenchmarkCloseRepository {

    suspend fun fetch(index: BenchmarkIndex, from: LocalDate, to: LocalDate): List<BenchmarkClose>

    suspend fun upsertAll(closes: List<BenchmarkClose>)

    // The earliest stored date for the index — where a manual re-check starts. Null when the index
    // has no stored point yet.
    suspend fun fetchEarliest(index: BenchmarkIndex): LocalDate?

    // When the index last contributed a point from a provider. Null when it was never fetched.
    suspend fun fetchLastFetchedAt(index: BenchmarkIndex): LocalDateTime?
}
