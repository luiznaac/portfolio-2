package dev.agner.portfolio.usecase.performance.benchmark

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// Whether a stored series is dense enough to price the whole period. A truncated or thin series is
// a data gap, not a flat zero: the caller reads an absent benchmark instead of a fabricated return.
object BenchmarkCoverage {

    fun covers(
        index: BenchmarkIndex,
        observations: List<BenchmarkObservation>,
        from: LocalDate,
        to: LocalDate,
    ): Boolean = when (index.kind) {
        BenchmarkKind.INDEX_LEVEL -> coversLevel(observations, from, to)
        BenchmarkKind.DAILY_RATE -> coversDailyRate(observations, from, to)
        BenchmarkKind.MONTHLY_VARIATION -> observations.any { it.date in from..to }
    }

    // A level needs a close before the period to measure from, no older than ten days, and a close
    // within five days of the period end to measure to.
    private fun coversLevel(
        observations: List<BenchmarkObservation>,
        from: LocalDate,
        to: LocalDate,
    ): Boolean {
        val sorted = observations.sortedBy { it.date }
        val base = sorted.lastOrNull { it.date < from } ?: return false
        val end = sorted.lastOrNull { it.date <= to } ?: return false
        return base.date >= from.daysBefore(BenchmarkCadence.LEVEL_BASE_LOOKBACK_DAYS) &&
            end.date >= to.daysBefore(BenchmarkCadence.LEVEL_END_TOLERANCE_DAYS)
    }

    // A daily rate compounds every observation in the period, so a late start, an early end or an
    // internal hole would silently undercount the rate rather than read as absent.
    private fun coversDailyRate(
        observations: List<BenchmarkObservation>,
        from: LocalDate,
        to: LocalDate,
    ): Boolean {
        val inPeriod = observations.sortedBy { it.date }.filter { it.date in from..to }
        if (inPeriod.isEmpty()) return false
        return inPeriod.first().date <= from.daysAfter(BenchmarkCadence.DAILY_BOUND_TOLERANCE_DAYS) &&
            inPeriod.last().date >= to.daysBefore(BenchmarkCadence.DAILY_BOUND_TOLERANCE_DAYS) &&
            inPeriod.zipWithNext().all { (previous, next) ->
                previous.date.daysUntil(next.date) <= BenchmarkCadence.DAILY_MAX_INTERNAL_GAP_DAYS
            }
    }

    private fun LocalDate.daysBefore(days: Int) = this.minus(days, DateTimeUnit.DAY)

    private fun LocalDate.daysAfter(days: Int) = this.plus(days, DateTimeUnit.DAY)
}
