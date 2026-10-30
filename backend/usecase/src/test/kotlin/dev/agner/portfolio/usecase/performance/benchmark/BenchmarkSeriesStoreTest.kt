package dev.agner.portfolio.usecase.performance.benchmark

import dev.agner.portfolio.usecase.performance.gateway.IBenchmarkSeriesSource
import dev.agner.portfolio.usecase.performance.repository.IBenchmarkCloseRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BenchmarkSeriesStoreTest : StringSpec({

    "serves a covered range without calling a provider" {
        val repository = FakeCloseRepository(
            ibovSeries(
                "2025-12-30",
                "2026-01-02",
                "2026-01-06",
                "2026-01-10",
                "2026-01-14",
                "2026-01-18",
                "2026-01-22",
                "2026-01-26",
                "2026-01-30",
            ),
        )
        val source = FakeSource()

        val series = store(repository, source).fetchSeries(BenchmarkIndex.IBOV, januaryStart, januaryEnd)

        source.calls shouldBe emptyList()
        series.size shouldBe 8
    }

    "fills an internal hole from the chain and stores it" {
        val repository = FakeCloseRepository(
            ibovSeries("2026-01-02", "2026-01-06", "2026-01-10", "2026-01-25", "2026-01-30"),
        )
        val source = FakeSource(
            points = listOf(BenchmarkObservation(LocalDate.parse("2026-01-15"), BigDecimal("105"))),
        )

        val series = store(repository, source).fetchSeries(BenchmarkIndex.IBOV, januaryStart, januaryEnd)

        source.calls shouldBe listOf(range("2026-01-11", "2026-01-24"))
        series.map { it.date } shouldContain LocalDate.parse("2026-01-15")
        repository.all().map { it.date } shouldContain LocalDate.parse("2026-01-15")
    }

    "fills the window before the first stored row" {
        val repository = FakeCloseRepository(ibovSeries("2026-01-20", "2026-01-25", "2026-01-30"))
        val source = FakeSource(
            points = listOf(
                BenchmarkObservation(LocalDate.parse("2026-01-02"), BigDecimal("100")),
                BenchmarkObservation(LocalDate.parse("2026-01-07"), BigDecimal("101")),
                BenchmarkObservation(LocalDate.parse("2026-01-12"), BigDecimal("102")),
                BenchmarkObservation(LocalDate.parse("2026-01-17"), BigDecimal("103")),
            ),
        )

        val series = store(repository, source).fetchSeries(BenchmarkIndex.IBOV, januaryStart, januaryEnd)

        source.calls shouldBe listOf(range("2026-01-01", "2026-01-19"))
        series.first().date shouldBe LocalDate.parse("2026-01-02")
    }

    "fills the window after the last stored row" {
        val repository = FakeCloseRepository(ibovSeries("2026-01-02", "2026-01-07", "2026-01-10"))
        val source = FakeSource(
            points = listOf(
                BenchmarkObservation(LocalDate.parse("2026-01-15"), BigDecimal("100")),
                BenchmarkObservation(LocalDate.parse("2026-01-20"), BigDecimal("101")),
                BenchmarkObservation(LocalDate.parse("2026-01-25"), BigDecimal("102")),
                BenchmarkObservation(LocalDate.parse("2026-01-30"), BigDecimal("103")),
            ),
        )

        val series = store(repository, source).fetchSeries(BenchmarkIndex.IBOV, januaryStart, januaryEnd)

        source.calls shouldBe listOf(range("2026-01-11", "2026-01-31"))
        series.last().date shouldBe LocalDate.parse("2026-01-30")
    }

    "an empty provider response never erases the stored rows" {
        val stored = ibovSeries("2026-01-02", "2026-01-07", "2026-01-10")
        val repository = FakeCloseRepository(stored)
        val source = FakeSource(points = emptyList())

        val series = store(repository, source).fetchSeries(BenchmarkIndex.IBOV, januaryStart, januaryEnd)

        series.map { it.date } shouldBe stored.map { it.date }
        repository.all().map { it.date } shouldBe stored.map { it.date }
    }

    "falls through the ordered chain until the whole window is covered" {
        val repository = FakeCloseRepository(emptyList())
        val brapi = FakeSource(
            source = BenchmarkSource.BRAPI,
            order = 10,
            points = listOf(
                BenchmarkObservation(LocalDate.parse("2026-01-20"), BigDecimal("100")),
                BenchmarkObservation(LocalDate.parse("2026-01-25"), BigDecimal("101")),
                BenchmarkObservation(LocalDate.parse("2026-01-30"), BigDecimal("102")),
            ),
        )
        val yahoo = FakeSource(
            source = BenchmarkSource.YAHOO,
            order = 20,
            points = listOf(
                BenchmarkObservation(LocalDate.parse("2026-01-02"), BigDecimal("90")),
                BenchmarkObservation(LocalDate.parse("2026-01-07"), BigDecimal("91")),
                BenchmarkObservation(LocalDate.parse("2026-01-12"), BigDecimal("92")),
                BenchmarkObservation(LocalDate.parse("2026-01-17"), BigDecimal("93")),
            ),
        )

        val series = store(repository, brapi, yahoo)
            .fetchSeries(BenchmarkIndex.IBOV, januaryStart, januaryEnd)

        brapi.calls shouldBe listOf(range("2026-01-01", "2026-01-31"))
        yahoo.calls shouldBe listOf(range("2026-01-01", "2026-01-19"))
        series.map { it.date } shouldContainExactly listOf(
            LocalDate.parse("2026-01-02"),
            LocalDate.parse("2026-01-07"),
            LocalDate.parse("2026-01-12"),
            LocalDate.parse("2026-01-17"),
            LocalDate.parse("2026-01-20"),
            LocalDate.parse("2026-01-25"),
            LocalDate.parse("2026-01-30"),
        )
        repository.all().first { it.date == LocalDate.parse("2026-01-20") }.source shouldBe BenchmarkSource.BRAPI
        repository.all().first { it.date == LocalDate.parse("2026-01-02") }.source shouldBe BenchmarkSource.YAHOO
    }

    "never stores a point the provider returns outside the missing window" {
        val repository = FakeCloseRepository(ibovSeries("2026-01-20", "2026-01-25", "2026-01-30"))
        val source = FakeSource(
            points = listOf(
                BenchmarkObservation(LocalDate.parse("2026-01-02"), BigDecimal("100")),
                BenchmarkObservation(LocalDate.parse("2026-02-10"), BigDecimal("999")),
            ),
        )

        store(repository, source).fetchSeries(BenchmarkIndex.IBOV, januaryStart, januaryEnd)

        repository.all().map { it.date } shouldContain LocalDate.parse("2026-01-02")
        repository.all().map { it.date } shouldNotContain LocalDate.parse("2026-02-10")
    }

    "returns a real IBOV level for both a 2018 and a 2020 stored range" {
        val repository = FakeCloseRepository(
            listOf(
                close("2017-12-29", "70000"),
                close("2018-01-02", "76000"),
                close("2018-01-06", "78000"),
                close("2018-01-10", "80000"),
                close("2018-01-14", "81000"),
                close("2018-01-18", "82000"),
                close("2018-01-22", "83000"),
                close("2018-01-26", "84000"),
                close("2018-01-30", "85000"),
                close("2019-12-30", "115000"),
                close("2020-01-02", "96000"),
                close("2020-01-06", "97000"),
                close("2020-01-10", "98000"),
                close("2020-01-14", "99000"),
                close("2020-01-18", "100000"),
                close("2020-01-22", "101000"),
                close("2020-01-26", "102000"),
                close("2020-01-30", "95000"),
            ),
        )
        val source = FakeSource()
        val subject = store(repository, source)

        subject.fetchSeries(BenchmarkIndex.IBOV, LocalDate.parse("2017-12-22"), LocalDate.parse("2018-01-31"))
            .last().value.compareTo(BigDecimal("85000")) shouldBe 0
        subject.fetchSeries(BenchmarkIndex.IBOV, LocalDate.parse("2019-12-22"), LocalDate.parse("2020-01-31"))
            .last().value.compareTo(BigDecimal("95000")) shouldBe 0
        source.calls shouldBe emptyList()
    }

    "fills a missing IPCA month without churning a healthy monthly series" {
        val healthy = FakeCloseRepository(
            listOf(
                ipcaClose("2025-12-01", "0.30"),
                ipcaClose("2026-01-01", "0.40"),
                ipcaClose("2026-02-01", "0.50"),
            ),
        )
        val healthySource = FakeSource(source = BenchmarkSource.SGS, indexes = setOf(BenchmarkIndex.IPCA))

        store(healthy, healthySource).fetchSeries(
            BenchmarkIndex.IPCA,
            LocalDate.parse("2025-12-22"),
            LocalDate.parse("2026-02-28"),
        )
        healthySource.calls shouldBe emptyList()

        val missingFebruary = FakeCloseRepository(
            listOf(ipcaClose("2026-01-01", "0.40"), ipcaClose("2026-03-01", "0.50")),
        )
        val gapSource = FakeSource(
            source = BenchmarkSource.SGS,
            indexes = setOf(BenchmarkIndex.IPCA),
            points = listOf(BenchmarkObservation(LocalDate.parse("2026-02-01"), BigDecimal("0.45"))),
        )

        val series = store(missingFebruary, gapSource).fetchSeries(
            BenchmarkIndex.IPCA,
            LocalDate.parse("2025-12-22"),
            LocalDate.parse("2026-03-31"),
        )

        gapSource.calls shouldBe listOf(range("2026-01-02", "2026-02-28"))
        series.map { it.date } shouldContain LocalDate.parse("2026-02-01")
    }

    "a deleted month reads absent, never zero" {
        val repository = FakeCloseRepository(
            ibovSeries("2026-01-02", "2026-01-07", "2026-01-12", "2026-01-17", "2026-01-30") +
                ibovSeries("2026-03-02", "2026-03-07", "2026-03-12", "2026-03-17", "2026-03-30"),
        )
        val source = FakeSource(points = emptyList())

        val february = store(repository, source)
            .fetchSeries(BenchmarkIndex.IBOV, LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"))

        february shouldBe emptyList()
        BenchmarkCalculator.returnFor(
            BenchmarkIndex.IBOV,
            february,
            LocalDate.parse("2026-02-01"),
            LocalDate.parse("2026-02-28"),
        ) shouldBe null
    }

    "backfills every index from the requested start to today" {
        val clock = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC)
        val source = FakeSource(
            indexes = BenchmarkIndex.entries.toSet(),
            points = listOf(BenchmarkObservation(LocalDate.parse("2026-02-27"), BigDecimal("1"))),
        )

        val report = store(FakeCloseRepository(emptyList()), source, clock = clock)
            .backfill(LocalDate.parse("2018-01-01"))

        report.indexes.map { it.index } shouldBe BenchmarkIndex.entries.toList()
        report.indexes.forEach { entry ->
            entry.from shouldBe LocalDate.parse("2018-01-01")
            entry.to shouldBe LocalDate.parse("2026-03-01")
            entry.points shouldBe 1
        }
    }
})

private val januaryStart = LocalDate.parse("2026-01-01")
private val januaryEnd = LocalDate.parse("2026-01-31")

private val testClock = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC)

private fun store(
    repository: IBenchmarkCloseRepository,
    vararg sources: IBenchmarkSeriesSource,
    clock: Clock = testClock,
) = BenchmarkSeriesStore(repository, sources.toList(), clock)

private fun close(date: String, level: String) = BenchmarkClose(
    index = BenchmarkIndex.IBOV,
    date = LocalDate.parse(date),
    level = BigDecimal(level),
    source = BenchmarkSource.SGS,
)

private fun ipcaClose(date: String, level: String) = BenchmarkClose(
    index = BenchmarkIndex.IPCA,
    date = LocalDate.parse(date),
    level = BigDecimal(level),
    source = BenchmarkSource.SGS,
)

private fun ibovSeries(vararg dates: String) = dates.mapIndexed { index, date ->
    BenchmarkClose(
        index = BenchmarkIndex.IBOV,
        date = LocalDate.parse(date),
        level = BigDecimal(100 + index),
        source = BenchmarkSource.SGS,
    )
}

private fun range(from: String, to: String) = LocalDate.parse(from)..LocalDate.parse(to)

private class FakeCloseRepository(initial: List<BenchmarkClose>) : IBenchmarkCloseRepository {

    private val rows = initial.toMutableList()

    override suspend fun fetch(index: BenchmarkIndex, from: LocalDate, to: LocalDate) = rows
        .filter { it.index == index && it.date >= from && it.date <= to }
        .sortedBy { it.date }

    override suspend fun upsertAll(closes: List<BenchmarkClose>) {
        rows.removeAll { row -> closes.any { it.index == row.index && it.date == row.date } }
        rows += closes
    }

    fun all() = rows.toList()
}

private class FakeSource(
    override val source: BenchmarkSource = BenchmarkSource.SGS,
    override val order: Int = 10,
    override val indexes: Set<BenchmarkIndex> = setOf(BenchmarkIndex.IBOV),
    private val points: List<BenchmarkObservation> = emptyList(),
) : IBenchmarkSeriesSource {

    val calls = mutableListOf<ClosedRange<LocalDate>>()

    override suspend fun fetchSeries(
        index: BenchmarkIndex,
        from: LocalDate,
        to: LocalDate,
    ): List<BenchmarkObservation> {
        calls += from..to
        return points
    }
}
