package dev.agner.portfolio.usecase.performance

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.monthlyclose.model.ClassTotalSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.HoldingSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.IncomeTotals
import dev.agner.portfolio.usecase.monthlyclose.model.OrdersSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
import dev.agner.portfolio.usecase.monthlyclose.model.TaxCloseSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.TickerIncomeSnapshot
import dev.agner.portfolio.usecase.monthlyclose.repository.ISnapshotRepository
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkObservation
import dev.agner.portfolio.usecase.performance.gateway.IBenchmarkGateway
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.repository.ITargetRepository
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import dev.agner.portfolio.usecase.tax.model.ExemptionMeter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class PerformanceServiceTest : StringSpec({

    val service = service(
        snapshots = listOf(january, february),
        targets = targets(),
        quotes = petr4Quotes,
    )

    "decomposes a closed month and blends its benchmark against the policy targets" {
        val performance = service.fetchForMonth(monthId = 2)!!

        performance.period shouldBe YearMonth.parse("2026-02")
        performance.decomposition.valueStart?.compareTo(BigDecimal("100000.00")) shouldBe 0
        performance.decomposition.valueEnd.compareTo(BigDecimal("103500.00")) shouldBe 0
        performance.decomposition.netAportes.compareTo(BigDecimal("2000.00")) shouldBe 0
        performance.decomposition.netIncome.compareTo(BigDecimal("500.00")) shouldBe 0
        performance.decomposition.returnPercentage?.compareTo(BigDecimal("1.0000")) shouldBe 0
        performance.xirr shouldNotBe null

        performance.benchmark.returnPercentage?.compareTo(BigDecimal("2.0000")) shouldBe 0

        val acoes = performance.classes.single()
        acoes.assetClass shouldBe AssetClass.ACOES
        acoes.decomposition.returnPercentage?.compareTo(BigDecimal("1.0000")) shouldBe 0
        acoes.benchmarkReturn?.compareTo(BigDecimal("2.0000")) shouldBe 0

        val petr4 = performance.tickers.single()
        petr4.ticker shouldBe "PETR4"
        petr4.assetClass shouldBe AssetClass.ACOES
        petr4.decomposition.returnPercentage?.compareTo(BigDecimal("1.0000")) shouldBe 0
    }

    "has no decomposition for the first snapshot in the series" {
        val performance = service.fetchForMonth(monthId = 1)!!

        performance.decomposition.valueStart shouldBe null
        performance.decomposition.returnPercentage shouldBe null
    }

    "returns null for a month with no snapshot" {
        service.fetchForMonth(monthId = 99) shouldBe null
    }

    "builds the series oldest first, with each month's xirr since inception" {
        val series = service.fetchSeries()

        series.months.map { it.period } shouldBe listOf(YearMonth.parse("2026-01"), YearMonth.parse("2026-02"))
        series.months.first().decomposition.returnPercentage shouldBe null
        series.months.last().decomposition.returnPercentage?.compareTo(BigDecimal("1.0000")) shouldBe 0
        series.months.last().xirr shouldNotBe null
        series.months.last().benchmarks.map { it.index } shouldBe
            listOf(BenchmarkIndex.CDI, BenchmarkIndex.IPCA, BenchmarkIndex.IBOV)
        series.months.last().benchmarks
            .first { it.index == BenchmarkIndex.IBOV }
            .returnPercentage?.compareTo(BigDecimal("2.0000")) shouldBe 0
        series.months.last().benchmarks
            .first { it.index == BenchmarkIndex.CDI }
            .returnPercentage shouldBe null
        series.months.last().classes.single().assetClass shouldBe AssetClass.ACOES
        series.months.last().classes.single().valueEnd.compareTo(BigDecimal("103500.00")) shouldBe 0
        series.xirr shouldNotBe null
    }
})

private fun service(
    snapshots: List<Snapshot>,
    targets: Targets,
    quotes: List<Quote>,
): PerformanceService {
    val snapshotRepository = mockk<ISnapshotRepository>(relaxed = true)
    coEvery { snapshotRepository.fetchAll() } returns snapshots
    val catalog = mockk<ICatalogRepository>(relaxed = true)
    coEvery { catalog.fetchAll() } returns listOf(petr4CatalogEntry)
    val quoteRepository = mockk<IQuoteRepository>(relaxed = true)
    coEvery { quoteRepository.fetchAllBy(any()) } answers { if (firstArg<String>() == "PETR4") quotes else emptyList() }
    val targetRepository = mockk<ITargetRepository>(relaxed = true)
    coEvery { targetRepository.fetchByMonth(any()) } returns targets
    val gateway = mockk<IBenchmarkGateway>(relaxed = true)
    coEvery { gateway.fetchSeries(any(), any(), any()) } answers { observationsFor(firstArg()) }

    return PerformanceService(
        snapshots = snapshotRepository,
        catalog = catalog,
        quotes = quoteRepository,
        targets = targetRepository,
        benchmarks = gateway,
    )
}

private fun observationsFor(index: BenchmarkIndex) = when (index) {
    BenchmarkIndex.IBOV -> listOf(
        BenchmarkObservation(LocalDate.parse("2026-01-30"), BigDecimal("100000")),
        BenchmarkObservation(LocalDate.parse("2026-02-27"), BigDecimal("102000")),
    )

    BenchmarkIndex.CDI, BenchmarkIndex.IPCA -> emptyList()
}

private fun targets() = Targets(
    classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("100"))),
    strategies = emptyList(),
    subClasses = emptyList(),
    tickers = emptyList(),
)

private val january = snapshot(
    id = 1,
    monthId = 1,
    period = YearMonth.parse("2026-01"),
    capital = "100000",
    aportes = "0",
    income = "0",
    quantity = "10000",
)

private val february = snapshot(
    id = 2,
    monthId = 2,
    period = YearMonth.parse("2026-02"),
    capital = "103500",
    aportes = "2000",
    income = "500",
    quantity = "10350",
)

private fun snapshot(
    id: Int,
    monthId: Int,
    period: YearMonth,
    capital: String,
    aportes: String,
    income: String,
    quantity: String,
) = Snapshot(
    id = id,
    monthId = monthId,
    createdAt = LocalDateTime.parse("2026-03-01T00:00:00"),
    state = CloseState(
        period = period,
        capital = BigDecimal(capital),
        floor = BigDecimal.ZERO,
        aportes = BigDecimal(aportes),
        caixa = BigDecimal.ZERO,
        holdings = listOf(HoldingSnapshot("PETR4", BigDecimal(quantity), BigDecimal("10"))),
        attributions = emptyList(),
        classes = listOf(
            ClassTotalSnapshot(
                assetClass = AssetClass.ACOES,
                target = BigDecimal("100"),
                ideal = BigDecimal(capital),
                actual = BigDecimal(capital),
                deficit = BigDecimal.ZERO,
                drift = BigDecimal.ZERO,
                unattributed = BigDecimal.ZERO,
            ),
        ),
        income = IncomeTotals(BigDecimal(income), BigDecimal.ZERO, BigDecimal(income)),
        incomeByTicker = listOf(TickerIncomeSnapshot("PETR4", BigDecimal(income))),
        orders = OrdersSnapshot(emptyList(), emptyList(), emptyList()),
        tax = TaxCloseSnapshot(
            carry = emptyList(),
            meter = ExemptionMeter(
                grossSales = BigDecimal.ZERO,
                exemptionLimit = BigDecimal("20000"),
                exceeded = false,
                realizedGain = BigDecimal.ZERO,
                taxableGain = BigDecimal.ZERO,
                rate = BigDecimal("15"),
                tax = BigDecimal.ZERO,
            ),
        ),
    ),
)

private val petr4Quotes = listOf(
    Quote("PETR4", LocalDate.parse("2026-01-30"), BigDecimal("10"), QuoteSource.COTAHIST),
    Quote("PETR4", LocalDate.parse("2026-02-27"), BigDecimal("10"), QuoteSource.COTAHIST),
)

private val petr4CatalogEntry = CatalogEntry(
    ticker = "PETR4",
    shortName = "PETROBRAS",
    bdiType = "02",
    isin = null,
    assetKind = AssetKind.STOCK,
    assetClass = AssetClass.ACOES,
    firstSeen = LocalDate.parse("2020-01-01"),
    lastSeen = LocalDate.parse("2026-02-27"),
)
