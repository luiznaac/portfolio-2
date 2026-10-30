package dev.agner.portfolio.usecase.performance.benchmark

import dev.agner.portfolio.usecase.commons.dayBefore
import dev.agner.portfolio.usecase.commons.nextDay
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.performance.gateway.IBenchmarkGateway
import dev.agner.portfolio.usecase.performance.gateway.IBenchmarkSeriesSource
import dev.agner.portfolio.usecase.performance.model.BenchmarkBackfillEntry
import dev.agner.portfolio.usecase.performance.model.BenchmarkBackfillReport
import dev.agner.portfolio.usecase.performance.repository.IBenchmarkCloseRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

// The benchmark series lives in the database, not at the provider: every read serves the stored
// range and only the windows that are missing (before the first row, after the last, or an internal
// hole) hit the index's ordered chain. A fetch upserts what it found and never deletes; an empty
// provider response leaves the stored rows exactly as they were.
@Service
class BenchmarkSeriesStore(
    private val repository: IBenchmarkCloseRepository,
    sources: List<IBenchmarkSeriesSource>,
    private val clock: Clock,
) : IBenchmarkGateway {

    private val orderedSources = sources.sortedBy { it.order }

    override suspend fun fetchSeries(
        index: BenchmarkIndex,
        from: LocalDate,
        to: LocalDate,
    ): List<BenchmarkObservation> {
        if (from > to) return emptyList()

        val stored = repository.fetch(index, from, to).associate { it.date to it.level }
        val points = orderedSources
            .filter { index in it.indexes }
            .fold(stored) { known, source -> fill(index, known, from, to, source) }

        return points.entries.sortedBy { it.key }.map { BenchmarkObservation(it.key, it.value) }
    }

    // The manual data action: one pass over every index from the requested start to today. It has no
    // scheduler behind it — freshness is the lazy gap-fill above plus this call.
    suspend fun backfill(from: LocalDate): BenchmarkBackfillReport {
        val to = LocalDate.today(clock)
        return BenchmarkBackfillReport(
            indexes = BenchmarkIndex.entries.map { index ->
                val points = fetchSeries(index, from, to)
                BenchmarkBackfillEntry(index = index, from = from, to = to, points = points.size)
            },
        )
    }

    private suspend fun fill(
        index: BenchmarkIndex,
        known: Map<LocalDate, BigDecimal>,
        from: LocalDate,
        to: LocalDate,
        source: IBenchmarkSeriesSource,
    ): Map<LocalDate, BigDecimal> {
        val windows = missingWindows(index, known.keys, from, to)
        if (windows.isEmpty()) return known

        val fetched = windows.flatMap { window ->
            source.fetchSeries(index, window.from, window.to).filter { it.date in window }
        }
        if (fetched.isEmpty()) return known

        repository.upsertAll(fetched.map { BenchmarkClose(index, it.date, it.value, source.source) })
        return known + fetched.associate { it.date to it.value }
    }

    // A gap is missing only when it exceeds what a healthy series may skip: a weekend plus a holiday
    // for a daily series, the better part of a month for a monthly one. Anything smaller is a normal
    // publication cadence, not a reason to call a provider.
    private fun missingWindows(
        index: BenchmarkIndex,
        dates: Collection<LocalDate>,
        from: LocalDate,
        to: LocalDate,
    ): List<Window> {
        val tolerances = tolerancesFor(index)
        val sorted = dates.filter { it >= from && it <= to }.distinct().sorted()
        if (sorted.isEmpty()) return listOf(Window(from = from, to = to))

        val first = sorted.first()
        val last = sorted.last()
        return buildList {
            if (from.daysUntil(first) > tolerances.leading) add(Window(from = from, to = first.dayBefore()))
            sorted.zipWithNext()
                .filter { (previous, next) -> previous.daysUntil(next) > tolerances.internal }
                .forEach { (previous, next) -> add(Window(from = previous.nextDay(), to = next.dayBefore())) }
            if (last.daysUntil(to) > tolerances.trailing) add(Window(from = last.nextDay(), to = to))
        }
    }

    // The callers ask for ten days before the period so an index level has a base to measure from,
    // so a healthy daily series can lag the request start by that lookback without being a gap. A
    // monthly series is observed once in its own month, so a healthy first point can sit up to the
    // full month behind the request start; an internal step of a month is still one observation, so
    // only a wider gap is a hole.
    private fun tolerancesFor(index: BenchmarkIndex) = when (index.kind) {
        BenchmarkKind.MONTHLY_VARIATION -> Tolerances(
            leading = BenchmarkCadence.MONTHLY_LEADING_TOLERANCE_DAYS,
            trailing = BenchmarkCadence.MONTHLY_TOLERANCE_DAYS,
            internal = BenchmarkCadence.MONTHLY_TOLERANCE_DAYS,
        )

        BenchmarkKind.DAILY_RATE, BenchmarkKind.INDEX_LEVEL -> Tolerances(
            leading = BenchmarkCadence.REQUEST_LEADING_TOLERANCE_DAYS,
            trailing = BenchmarkCadence.DAILY_BOUND_TOLERANCE_DAYS,
            internal = BenchmarkCadence.DAILY_MAX_INTERNAL_GAP_DAYS,
        )
    }

    private data class Tolerances(val leading: Int, val trailing: Int, val internal: Int)

    private data class Window(val from: LocalDate, val to: LocalDate) {
        operator fun contains(date: LocalDate) = date >= from && date <= to
    }
}
