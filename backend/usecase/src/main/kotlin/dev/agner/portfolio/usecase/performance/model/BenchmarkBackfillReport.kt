package dev.agner.portfolio.usecase.performance.model

import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import kotlinx.datetime.LocalDate

// The outcome of a one-off benchmark backfill: one row per index, with the stored points the window
// now holds. It is a curl/runbook action, not a scheduled sync.
data class BenchmarkBackfillReport(val indexes: List<BenchmarkBackfillEntry>)

data class BenchmarkBackfillEntry(
    val index: BenchmarkIndex,
    val from: LocalDate,
    val to: LocalDate,
    val points: Int,
)
