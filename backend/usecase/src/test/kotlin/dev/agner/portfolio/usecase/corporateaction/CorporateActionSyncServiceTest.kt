package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiFetch
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiIncome
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiRename
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiStockAction
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiStockEvents
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiWindow
import dev.agner.portfolio.usecase.corporateaction.gateway.IBrapiCorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSync
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSyncCreation
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionSyncRepository
import dev.agner.portfolio.usecase.income.IncomeService
import dev.agner.portfolio.usecase.income.model.Income
import dev.agner.portfolio.usecase.income.model.IncomeCreation
import dev.agner.portfolio.usecase.income.model.IncomeStatus
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.model.Provenance
import dev.agner.portfolio.usecase.income.model.TaxNature
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CorporateActionSyncServiceTest : StringSpec({
    val source = mockk<IBrapiCorporateActionSource>()
    val trades = mockk<ITradeRepository>()
    val catalog = mockk<CatalogService>()
    val aliases = mockk<TickerAliasResolver>()
    val corporateActions = mockk<CorporateActionService>(relaxed = true)
    val incomes = mockk<IncomeService>(relaxed = true)
    val positions = mockk<PositionService>(relaxed = true)
    val syncs = mockk<ICorporateActionSyncRepository>()
    val clock = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC)
    val service = CorporateActionSyncService(
        source,
        trades,
        catalog,
        aliases,
        positions,
        corporateActions,
        incomes,
        syncs,
        clock,
    )

    beforeTest {
        clearAllMocks()
        coEvery { trades.fetchAll() } returns listOf(
            buy(ticker = "PETR4", date = "2018-03-05"),
            buy(ticker = "MXRF11", date = "2020-07-01"),
        )
        coEvery { aliases.aliasesOf(any()) } answers { setOf(firstArg()) }
        coEvery { aliases.canonicalAll(any()) } answers {
            firstArg<Collection<String>>().associateWith { it }
        }
        coEvery { catalog.fetchAll() } returns listOf(stockEntry, fundEntry)
        coEvery { positions.positionOn(any(), any()) } returns
            Position("PETR4", BigDecimal("100"), BigDecimal("10"))
        coEvery { source.fetchRenames(any()) } returns BrapiFetch("renames-body", emptyList())
        coEvery { source.fetchStockEvents(any(), any()) } returns
            BrapiFetch("stock-body", BrapiStockEvents())
        coEvery { source.fetchFundEvents(any(), any()) } returns BrapiFetch("fund-body", emptyList())
        coEvery { corporateActions.applyBrapi(any()) } returns BrapiApplyResult(applied = 0, parked = 0)
        coEvery { incomes.importEvents(any()) } returns emptyList()
        coEvery { syncs.save(any()) } answers { firstArg<CorporateActionSyncCreation>().toReport() }
    }

    "queries every ledger ticker over the earliest trade through today" {
        val renames = slot<List<String>>()
        val stocks = slot<List<String>>()
        val funds = slot<List<String>>()
        val window = slot<BrapiWindow>()
        coEvery { source.fetchRenames(capture(renames)) } returns BrapiFetch("renames-body", emptyList())
        coEvery { source.fetchStockEvents(capture(stocks), capture(window)) } returns
            BrapiFetch("stock-body", BrapiStockEvents())
        coEvery { source.fetchFundEvents(capture(funds), any()) } returns BrapiFetch("fund-body", emptyList())

        val report = service.sync()

        renames.captured shouldContainExactlyInAnyOrder listOf("MXRF11", "PETR4")
        stocks.captured shouldBe listOf("PETR4")
        funds.captured shouldBe listOf("MXRF11")
        window.captured shouldBe BrapiWindow(LocalDate.parse("2018-03-05"), LocalDate.parse("2026-11-17"))
        report.windowFrom shouldBe LocalDate.parse("2018-03-05")
        report.windowTo shouldBe LocalDate.parse("2026-11-17")
        report.asOf shouldBe LocalDate.parse("2026-11-17")
        report.tickerCount shouldBe 2
    }

    "queries every code a renamed paper traded under" {
        coEvery { aliases.aliasesOf("OLD3") } returns setOf("OLD3", "NEW3")
        coEvery { aliases.aliasesOf("NEW3") } returns setOf("OLD3", "NEW3")
        coEvery { trades.fetchAll() } returns listOf(buy(ticker = "OLD3", date = "2018-03-05"))
        coEvery { catalog.fetchAll() } returns listOf(stockEntry.copy(ticker = "NEW3"))
        val renames = slot<List<String>>()
        coEvery { source.fetchRenames(capture(renames)) } returns BrapiFetch("renames-body", emptyList())

        val report = service.sync()

        renames.captured shouldContainExactlyInAnyOrder listOf("OLD3", "NEW3")
        report.tickerCount shouldBe 2
    }

    "maps renames, stock events and cash income onto the domain shapes" {
        coEvery { source.fetchRenames(any()) } returns BrapiFetch(
            "renames-body",
            listOf(
                BrapiRename(
                    oldSymbol = "BIDI11",
                    newSymbol = "INBR32",
                    canonicalSymbol = "INBR32",
                    effectiveDate = LocalDate.parse("2022-06-23"),
                    conversionRatio = BigDecimal("1.1"),
                ),
            ),
        )
        coEvery { source.fetchStockEvents(any(), any()) } returns BrapiFetch(
            "stock-body",
            BrapiStockEvents(
                cashDividends = listOf(
                    BrapiIncome(
                        symbol = "PETR4",
                        label = "JCP",
                        rate = BigDecimal("0.20"),
                        rawRate = BigDecimal("0.25"),
                        lastDatePrior = LocalDate.parse("2026-06-01"),
                        paymentDate = LocalDate.parse("2026-08-20"),
                    ),
                    BrapiIncome(
                        symbol = "PETR4",
                        label = "DIVIDENDO",
                        rate = BigDecimal("0.40"),
                        lastDatePrior = LocalDate.parse("2026-06-01"),
                        paymentDate = LocalDate.parse("2026-08-20"),
                    ),
                ),
                stockDividends = listOf(
                    BrapiStockAction(
                        symbol = "PETR4",
                        label = "DESDOBRAMENTO",
                        factor = BigDecimal("2"),
                        exDate = LocalDate.parse("2008-04-25"),
                    ),
                    BrapiStockAction(
                        symbol = "PETR4",
                        label = "GRUPAMENTO",
                        factor = BigDecimal("0.01"),
                        exDate = LocalDate.parse("2000-06-22"),
                    ),
                    BrapiStockAction(
                        symbol = "PETR4",
                        label = "BONIFICACAO",
                        factor = BigDecimal("1.3333334"),
                        exDate = LocalDate.parse("1994-03-26"),
                    ),
                ),
            ),
        )
        coEvery { source.fetchFundEvents(any(), any()) } returns BrapiFetch(
            "fund-body",
            listOf(
                BrapiIncome(
                    symbol = "MXRF11",
                    label = "RENDIMENTO",
                    rate = BigDecimal("0.10"),
                    rawRate = BigDecimal("0.10"),
                    lastDatePrior = LocalDate.parse("2026-10-30"),
                    paymentDate = LocalDate.parse("2026-11-16"),
                ),
                BrapiIncome(
                    symbol = "MXRF11",
                    label = "AMORTIZAÇÃO",
                    rate = BigDecimal("0.50"),
                    rawRate = BigDecimal("0.50"),
                    exDate = LocalDate.parse("2026-09-30"),
                    paymentDate = LocalDate.parse("2026-10-15"),
                ),
            ),
        )
        val actions = slot<List<CorporateActionCreation>>()
        coEvery { corporateActions.applyBrapi(capture(actions)) } returns
            BrapiApplyResult(applied = 5, parked = 0)
        val income = slot<List<IncomeCreation>>()
        coEvery { incomes.importEvents(capture(income)) } returns listOf(incomeSample)

        val report = service.sync()

        val rename = actions.captured.single { it.kind == CorporateActionKind.TICKER_CHANGE }
        rename.ticker shouldBe "BIDI11"
        rename.targetTicker shouldBe "INBR32"
        rename.exDate shouldBe LocalDate.parse("2022-06-23")
        rename.ratio?.compareTo(BigDecimal("1.1")) shouldBe 0
        rename.source shouldBe CorporateActionSource.BRAPI

        val split = actions.captured.single { it.kind == CorporateActionKind.SPLIT }
        split.ratio?.compareTo(BigDecimal("2")) shouldBe 0
        split.exDate shouldBe LocalDate.parse("2008-04-25")
        val reverse = actions.captured.single { it.kind == CorporateActionKind.REVERSE_SPLIT }
        reverse.ratio?.compareTo(BigDecimal("100")) shouldBe 0
        val bonus = actions.captured.single { it.kind == CorporateActionKind.BONUS_SHARES }
        bonus.ratio?.compareTo(BigDecimal("0.3333334")) shouldBe 0

        val amortization = actions.captured.single { it.kind == CorporateActionKind.AMORTIZATION }
        amortization.declaredValuePerShare?.compareTo(BigDecimal("0.50")) shouldBe 0
        amortization.exDate shouldBe LocalDate.parse("2026-09-30")

        income.captured.map { it.type } shouldBe
            listOf(IncomeType.JCP, IncomeType.DIVIDEND, IncomeType.FUND_INCOME)
        val jcp = income.captured.first()
        jcp.grossAmount.compareTo(BigDecimal("25.00")) shouldBe 0
        jcp.estimated shouldBe false
        jcp.date shouldBe LocalDate.parse("2026-08-20")
        jcp.provenance shouldBe Provenance.BRAPI
        val dividend = income.captured[1]
        dividend.grossAmount.compareTo(BigDecimal("40.00")) shouldBe 0
        dividend.estimated shouldBe true
        income.captured[2].grossAmount.compareTo(BigDecimal("10.00")) shouldBe 0

        report.fetched shouldBe 8
        report.applied shouldBe 5
        report.income shouldBe 1
    }

    "an event with no ex-date reaches the engine as a parked candidate" {
        coEvery { source.fetchStockEvents(any(), any()) } returns BrapiFetch(
            "stock-body",
            BrapiStockEvents(
                stockDividends = listOf(
                    BrapiStockAction(
                        symbol = "PETR4",
                        label = "DESDOBRAMENTO",
                        factor = BigDecimal("2"),
                        exDate = null,
                    ),
                ),
            ),
        )
        val actions = slot<List<CorporateActionCreation>>()
        coEvery { corporateActions.applyBrapi(capture(actions)) } returns
            BrapiApplyResult(applied = 0, parked = 1)

        val report = service.sync()

        actions.captured.single().exDate shouldBe null
        report.parked shouldBe 1
    }

    "a failed batch reports its tickers without losing the other sources" {
        coEvery { source.fetchStockEvents(any(), any()) } throws BrapiFetchException("429 from brapi")
        coEvery { source.fetchRenames(any()) } returns BrapiFetch(
            "renames-body",
            listOf(
                BrapiRename(
                    oldSymbol = "MXRF11",
                    newSymbol = "MXRF11",
                    canonicalSymbol = "MXRF11",
                    effectiveDate = LocalDate.parse("2024-01-29"),
                ),
            ),
        )
        val actions = slot<List<CorporateActionCreation>>()
        coEvery { corporateActions.applyBrapi(capture(actions)) } returns
            BrapiApplyResult(applied = 1, parked = 0)
        val saved = slot<CorporateActionSyncCreation>()
        coEvery { syncs.save(capture(saved)) } answers { firstArg<CorporateActionSyncCreation>().toReport() }

        val report = service.sync()

        report.failedTickers shouldBe listOf("PETR4")
        actions.captured.single().kind shouldBe CorporateActionKind.TICKER_CHANGE
        report.applied shouldBe 1
        saved.captured.failedTickers shouldBe listOf("PETR4")
    }

    "persists the audit with its raw payload" {
        val saved = slot<CorporateActionSyncCreation>()
        coEvery { syncs.save(capture(saved)) } answers { firstArg<CorporateActionSyncCreation>().toReport() }

        service.sync()

        saved.captured.windowFrom shouldBe LocalDate.parse("2018-03-05")
        saved.captured.asOf shouldBe LocalDate.parse("2026-11-17")
        saved.captured.tickerCount shouldBe 2
        saved.captured.rawPayload shouldContain "renames-body"
        saved.captured.rawPayload shouldContain "stock-body"
        saved.captured.rawPayload shouldContain "fund-body"
    }

    "a missing token fails the sync loudly and writes no audit" {
        coEvery { source.fetchRenames(any()) } throws BrapiSyncException("BRAPI_TOKEN is not configured")

        shouldThrow<BrapiSyncException> { service.sync() }

        coVerify(exactly = 0) { syncs.save(any()) }
    }

    "an empty ledger makes no provider call" {
        coEvery { trades.fetchAll() } returns emptyList()

        val report = service.sync()

        report.tickerCount shouldBe 0
        coVerify(exactly = 0) { source.fetchRenames(any()) }
        coVerify(exactly = 0) { source.fetchStockEvents(any(), any()) }
        coVerify(exactly = 0) { source.fetchFundEvents(any(), any()) }
    }
})

private val stockEntry = CatalogEntry(
    ticker = "PETR4",
    shortName = "PETROBRAS PN",
    bdiType = "02",
    isin = "BRPETRACNPR6",
    assetKind = AssetKind.STOCK,
    assetClass = AssetClass.ACOES,
    firstSeen = LocalDate.parse("2018-01-02"),
    lastSeen = LocalDate.parse("2026-10-01"),
)

private val fundEntry = CatalogEntry(
    ticker = "MXRF11",
    shortName = "MAXI RENDA",
    bdiType = "12",
    isin = "BRMXRFCTF008",
    assetKind = AssetKind.FII,
    assetClass = AssetClass.FIIS,
    firstSeen = LocalDate.parse("2020-01-02"),
    lastSeen = LocalDate.parse("2026-10-01"),
)

private val incomeSample = Income(
    id = 1,
    ticker = "PETR4",
    date = LocalDate.parse("2026-08-20"),
    type = IncomeType.JCP,
    grossAmount = BigDecimal("25"),
    retainedAmount = BigDecimal.ZERO,
    netAmount = BigDecimal("25"),
    taxNature = TaxNature.INCOME,
    provenance = Provenance.BRAPI,
    status = IncomeStatus.RECEIVED,
)

private fun CorporateActionSyncCreation.toReport() = CorporateActionSync(
    id = 1,
    startedAt = startedAt,
    asOf = asOf,
    windowFrom = windowFrom,
    windowTo = windowTo,
    tickerCount = tickerCount,
    fetched = fetched,
    applied = applied,
    parked = parked,
    income = income,
    failedTickers = failedTickers,
)
