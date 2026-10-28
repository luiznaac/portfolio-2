package dev.agner.portfolio.usecase.performance

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.monthlyclose.model.AttributionSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.ClassTotalSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.HoldingSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.IncomeTotals
import dev.agner.portfolio.usecase.monthlyclose.model.OrdersSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
import dev.agner.portfolio.usecase.monthlyclose.model.StrategySliceSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.TaxCloseSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.TickerIncomeSnapshot
import dev.agner.portfolio.usecase.monthlyclose.repository.ISnapshotRepository
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkObservation
import dev.agner.portfolio.usecase.performance.gateway.IBenchmarkGateway
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerTarget
import dev.agner.portfolio.usecase.portfolio.repository.ITargetRepository
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
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
        acoes.strategies shouldBe emptyList()
        acoes.outside?.returnPercentage?.compareTo(BigDecimal("1.0000")) shouldBe 0

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

    "splits a shared ticker by quantity across the strategy slices and the outside row" {
        val performance = sharedService.fetchForMonth(monthId = 2)!!

        val acoes = performance.classes.single()
        val report = acoes.strategies.single { it.strategyId == 1 }
        val bucket = acoes.strategies.single { it.strategyId == 2 }
        report.strategy shouldBe "Top Dividendos"
        report.kind shouldBe StrategyKind.REPORT_DRIVEN
        bucket.strategy shouldBe "Legado"
        bucket.kind shouldBe StrategyKind.MANUAL

        report.decomposition.valueStart?.compareTo(BigDecimal("4000.00")) shouldBe 0
        report.decomposition.valueEnd.compareTo(BigDecimal("4400.00")) shouldBe 0
        report.decomposition.netAportes.compareTo(BigDecimal("440.00")) shouldBe 0
        report.decomposition.netIncome.compareTo(BigDecimal("40.00")) shouldBe 0
        report.decomposition.flow.compareTo(BigDecimal.ZERO) shouldBe 0
        report.decomposition.returnPercentage?.compareTo(BigDecimal("-2.0000")) shouldBe 0

        bucket.decomposition.valueStart?.compareTo(BigDecimal("6000.00")) shouldBe 0
        bucket.decomposition.valueEnd.compareTo(BigDecimal("6600.00")) shouldBe 0
        bucket.decomposition.netAportes.compareTo(BigDecimal("660.00")) shouldBe 0
        bucket.decomposition.netIncome.compareTo(BigDecimal("60.00")) shouldBe 0
        bucket.decomposition.returnPercentage?.compareTo(BigDecimal("-2.0000")) shouldBe 0

        val outside = acoes.outside!!
        outside.valueStart?.compareTo(BigDecimal("1000.00")) shouldBe 0
        outside.valueEnd.compareTo(BigDecimal("1100.00")) shouldBe 0
        outside.netAportes.compareTo(BigDecimal("110.00")) shouldBe 0
        outside.netIncome.compareTo(BigDecimal("10.00")) shouldBe 0
        outside.returnPercentage?.compareTo(BigDecimal("-2.0000")) shouldBe 0

        // The class line equals its slices plus the outside row.
        val start = acoes.strategies.fold(outside.valueStart!!) { total, slice ->
            total + (slice.decomposition.valueStart ?: BigDecimal.ZERO)
        }
        start.compareTo(acoes.decomposition.valueStart!!) shouldBe 0
        val end = acoes.strategies.fold(outside.valueEnd) { total, slice -> total + slice.decomposition.valueEnd }
        end.compareTo(acoes.decomposition.valueEnd) shouldBe 0
        val aportes = acoes.strategies.fold(outside.netAportes) { total, slice ->
            total + slice.decomposition.netAportes
        }
        aportes.compareTo(acoes.decomposition.netAportes) shouldBe 0
        val income = acoes.strategies.fold(outside.netIncome) { total, slice ->
            total + slice.decomposition.netIncome
        }
        income.compareTo(acoes.decomposition.netIncome) shouldBe 0

        // The ticker table keeps one row per ticker, not one per slice.
        performance.tickers.map { it.ticker } shouldBe listOf("PETR4", "VALE3")
    }

    "does not invent a prior slice book for a snapshot written before the strategy rung" {
        val performance = legacyService.fetchForMonth(monthId = 2)!!

        val acoes = performance.classes.single()
        acoes.strategies.forEach { it.decomposition.valueStart shouldBe null }
        acoes.outside!!.valueStart shouldBe null
        acoes.outside!!.returnPercentage shouldBe null

        // The class line still measures from its own prior value.
        acoes.decomposition.returnPercentage?.compareTo(BigDecimal("-2.0000")) shouldBe 0
    }

    "gives a Strategy that first appears this month a zero prior book" {
        val performance = newStrategyService.fetchForMonth(monthId = 2)!!

        val acoes = performance.classes.single()
        val report = acoes.strategies.single { it.strategyId == 1 }
        report.decomposition.valueStart?.compareTo(BigDecimal.ZERO) shouldBe 0
        report.decomposition.flow.compareTo(BigDecimal("4000.00")) shouldBe 0
        report.decomposition.returnPercentage shouldBe null
        acoes.outside!!.valueStart?.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    "nets each slice's bookkeeping flow instead of reading a transfer as return" {
        val performance = transferService.fetchForMonth(monthId = 2)!!

        val acoes = performance.classes.single()
        val report = acoes.strategies.single { it.strategyId == 1 }
        val bucket = acoes.strategies.single { it.strategyId == 2 }

        report.decomposition.flow.compareTo(BigDecimal("1000.00")) shouldBe 0
        report.decomposition.returnPercentage?.compareTo(BigDecimal("0.0000")) shouldBe 0
        bucket.decomposition.flow.compareTo(BigDecimal("-1000.00")) shouldBe 0
        bucket.decomposition.returnPercentage?.compareTo(BigDecimal("0.0000")) shouldBe 0

        val outside = acoes.outside!!
        outside.flow.compareTo(BigDecimal.ZERO) shouldBe 0
        outside.valueEnd.compareTo(BigDecimal.ZERO) shouldBe 0

        // The transfer nets to zero across the slices, so the class line carries no flow either.
        acoes.decomposition.returnPercentage?.compareTo(BigDecimal("0.0000")) shouldBe 0
    }

    "builds one frozen value series per Strategy, the bucket included" {
        val series = sharedService.fetchSeries()

        val strategies = series.months.last().strategies.associateBy { it.strategyId }
        strategies.keys shouldBe setOf(1, 2)
        strategies.getValue(1).strategy shouldBe "Top Dividendos"
        strategies.getValue(1).kind shouldBe StrategyKind.REPORT_DRIVEN
        strategies.getValue(1).valueEnd.compareTo(BigDecimal("4400.00")) shouldBe 0
        strategies.getValue(1).flow.compareTo(BigDecimal.ZERO) shouldBe 0
        strategies.getValue(2).strategy shouldBe "Legado"
        strategies.getValue(2).kind shouldBe StrategyKind.MANUAL
        strategies.getValue(2).valueEnd.compareTo(BigDecimal("6600.00")) shouldBe 0
        strategies.getValue(2).flow.compareTo(BigDecimal.ZERO) shouldBe 0
    }
})

private fun service(
    snapshots: List<Snapshot>,
    targets: Targets,
    quotes: List<Quote>,
    catalogEntries: List<CatalogEntry> = listOf(petr4CatalogEntry),
    strategies: List<Strategy> = strategyCatalog,
): PerformanceService {
    val snapshotRepository = mockk<ISnapshotRepository>(relaxed = true)
    coEvery { snapshotRepository.fetchAll() } returns snapshots
    val catalog = mockk<ICatalogRepository>(relaxed = true)
    coEvery { catalog.fetchAll() } returns catalogEntries
    val quoteRepository = mockk<IQuoteRepository>(relaxed = true)
    coEvery { quoteRepository.fetchAllBy(any()) } answers {
        quotes.filter { it.ticker == firstArg<String>() }
    }
    val targetRepository = mockk<ITargetRepository>(relaxed = true)
    coEvery { targetRepository.fetchByMonth(any()) } returns targets
    val strategyRepository = mockk<IStrategyRepository>(relaxed = true)
    coEvery { strategyRepository.fetchAll() } returns strategies
    val gateway = mockk<IBenchmarkGateway>(relaxed = true)
    coEvery { gateway.fetchSeries(any(), any(), any()) } answers { observationsFor(firstArg()) }

    return PerformanceService(
        snapshots = snapshotRepository,
        catalog = catalog,
        quotes = quoteRepository,
        targets = targetRepository,
        strategies = strategyRepository,
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

// The report Strategy names PETR4, so that ticker is inside it; the bucket owns every ticker it is
// attributed. A second holding (VALE3) is attributed to nobody and stays in the outside row.
private fun sharedTargets() = Targets(
    classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("100"))),
    strategies = listOf(StrategyTarget(AssetClass.ACOES, "Top Dividendos", BigDecimal("40"))),
    subClasses = emptyList(),
    tickers = listOf(TickerTarget("Top Dividendos", "PETR4", BigDecimal("100"))),
)

private val strategyCatalog = listOf(
    Strategy(1, "Top Dividendos", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN),
    Strategy(2, "Legado", AssetClass.ACOES, StrategyKind.MANUAL),
)

private val sharedQuotes = listOf(
    Quote("PETR4", LocalDate.parse("2026-01-30"), BigDecimal("10"), QuoteSource.COTAHIST),
    Quote("PETR4", LocalDate.parse("2026-02-27"), BigDecimal("11"), QuoteSource.COTAHIST),
    Quote("VALE3", LocalDate.parse("2026-01-30"), BigDecimal("10"), QuoteSource.COTAHIST),
    Quote("VALE3", LocalDate.parse("2026-02-27"), BigDecimal("11"), QuoteSource.COTAHIST),
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

private val sharedAttributions = listOf(
    AttributionSnapshot("PETR4", 1, "Top Dividendos", BigDecimal("400")),
    AttributionSnapshot("PETR4", 2, "Legado", BigDecimal("600")),
)

private val sharedJanuary = snapshot(
    id = 1,
    monthId = 1,
    period = YearMonth.parse("2026-01"),
    capital = "11000",
    aportes = "0",
    income = "0",
    quantity = "1000",
    holdings = listOf(
        HoldingSnapshot("PETR4", BigDecimal("1000"), BigDecimal("10")),
        HoldingSnapshot("VALE3", BigDecimal("100"), BigDecimal("10")),
    ),
    attributions = sharedAttributions,
    strategies = listOf(
        slice(id = 1, name = "Top Dividendos", kind = StrategyKind.REPORT_DRIVEN, valueEnd = "4000", flow = "0"),
        slice(id = 2, name = "Legado", kind = StrategyKind.MANUAL, valueEnd = "6000", flow = "0"),
    ),
)

private val sharedFebruary = snapshot(
    id = 2,
    monthId = 2,
    period = YearMonth.parse("2026-02"),
    capital = "12100",
    aportes = "1210",
    income = "110",
    quantity = "1000",
    holdings = listOf(
        HoldingSnapshot("PETR4", BigDecimal("1000"), BigDecimal("11")),
        HoldingSnapshot("VALE3", BigDecimal("100"), BigDecimal("11")),
    ),
    attributions = sharedAttributions,
    strategies = listOf(
        slice(id = 1, name = "Top Dividendos", kind = StrategyKind.REPORT_DRIVEN, valueEnd = "4400", flow = "0"),
        slice(id = 2, name = "Legado", kind = StrategyKind.MANUAL, valueEnd = "6600", flow = "0"),
    ),
    incomeByTicker = listOf(
        TickerIncomeSnapshot("PETR4", BigDecimal("100")),
        TickerIncomeSnapshot("VALE3", BigDecimal("10")),
    ),
)

private val transferJanuary = snapshot(
    id = 1,
    monthId = 1,
    period = YearMonth.parse("2026-01"),
    capital = "10000",
    aportes = "0",
    income = "0",
    quantity = "1000",
    attributions = listOf(
        AttributionSnapshot("PETR4", 1, "Top Dividendos", BigDecimal("300")),
        AttributionSnapshot("PETR4", 2, "Legado", BigDecimal("700")),
    ),
    strategies = listOf(
        slice(id = 1, name = "Top Dividendos", kind = StrategyKind.REPORT_DRIVEN, valueEnd = "3000", flow = "0"),
        slice(id = 2, name = "Legado", kind = StrategyKind.MANUAL, valueEnd = "7000", flow = "0"),
    ),
)

private val transferFebruary = snapshot(
    id = 2,
    monthId = 2,
    period = YearMonth.parse("2026-02"),
    capital = "10000",
    aportes = "0",
    income = "0",
    quantity = "1000",
    attributions = listOf(
        AttributionSnapshot("PETR4", 1, "Top Dividendos", BigDecimal("400")),
        AttributionSnapshot("PETR4", 2, "Legado", BigDecimal("600")),
    ),
    strategies = listOf(
        slice(id = 1, name = "Top Dividendos", kind = StrategyKind.REPORT_DRIVEN, valueEnd = "4000", flow = "1000"),
        slice(id = 2, name = "Legado", kind = StrategyKind.MANUAL, valueEnd = "6000", flow = "-1000"),
    ),
)

private val newStrategyJanuary = snapshot(
    id = 1,
    monthId = 1,
    period = YearMonth.parse("2026-01"),
    capital = "10000",
    aportes = "0",
    income = "0",
    quantity = "1000",
    attributions = listOf(AttributionSnapshot("PETR4", 2, "Legado", BigDecimal("1000"))),
    strategies = listOf(
        slice(id = 2, name = "Legado", kind = StrategyKind.MANUAL, valueEnd = "10000", flow = "0"),
    ),
)

private val newStrategyFebruary = snapshot(
    id = 2,
    monthId = 2,
    period = YearMonth.parse("2026-02"),
    capital = "10000",
    aportes = "0",
    income = "0",
    quantity = "1000",
    attributions = listOf(
        AttributionSnapshot("PETR4", 1, "Top Dividendos", BigDecimal("400")),
        AttributionSnapshot("PETR4", 2, "Legado", BigDecimal("600")),
    ),
    strategies = listOf(
        slice(id = 1, name = "Top Dividendos", kind = StrategyKind.REPORT_DRIVEN, valueEnd = "4000", flow = "4000"),
        slice(id = 2, name = "Legado", kind = StrategyKind.MANUAL, valueEnd = "6000", flow = "-4000"),
    ),
)

private fun slice(
    id: Int,
    name: String,
    kind: StrategyKind,
    valueEnd: String,
    flow: String,
) = StrategySliceSnapshot(
    strategyId = id,
    strategy = name,
    kind = kind,
    valueEnd = BigDecimal(valueEnd),
    flow = BigDecimal(flow),
)

private fun snapshot(
    id: Int,
    monthId: Int,
    period: YearMonth,
    capital: String,
    aportes: String,
    income: String,
    quantity: String,
    holdings: List<HoldingSnapshot> = listOf(
        HoldingSnapshot("PETR4", BigDecimal(quantity), BigDecimal("10")),
    ),
    attributions: List<AttributionSnapshot> = emptyList(),
    strategies: List<StrategySliceSnapshot> = emptyList(),
    incomeByTicker: List<TickerIncomeSnapshot> = listOf(TickerIncomeSnapshot("PETR4", BigDecimal(income))),
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
        holdings = holdings,
        attributions = attributions,
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
        strategies = strategies,
        income = IncomeTotals(BigDecimal(income), BigDecimal.ZERO, BigDecimal(income)),
        incomeByTicker = incomeByTicker,
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

private val vale3CatalogEntry = CatalogEntry(
    ticker = "VALE3",
    shortName = "VALE",
    bdiType = "02",
    isin = null,
    assetKind = AssetKind.STOCK,
    assetClass = AssetClass.ACOES,
    firstSeen = LocalDate.parse("2020-01-01"),
    lastSeen = LocalDate.parse("2026-02-27"),
)

private val sharedCatalog = listOf(petr4CatalogEntry, vale3CatalogEntry)

private val sharedService = service(
    snapshots = listOf(sharedJanuary, sharedFebruary),
    targets = sharedTargets(),
    quotes = sharedQuotes,
    catalogEntries = sharedCatalog,
)

private val transferService = service(
    snapshots = listOf(transferJanuary, transferFebruary),
    targets = sharedTargets(),
    quotes = petr4Quotes,
    catalogEntries = listOf(petr4CatalogEntry),
)

private val newStrategyService = service(
    snapshots = listOf(newStrategyJanuary, newStrategyFebruary),
    targets = sharedTargets(),
    quotes = petr4Quotes,
    catalogEntries = listOf(petr4CatalogEntry),
)

// The prior close predates the strategy rung: it froze the attribution ledger but no slices.
private val legacyService = service(
    snapshots = listOf(
        sharedJanuary.copy(state = sharedJanuary.state.copy(strategies = emptyList())),
        sharedFebruary,
    ),
    targets = sharedTargets(),
    quotes = sharedQuotes,
    catalogEntries = sharedCatalog,
)
