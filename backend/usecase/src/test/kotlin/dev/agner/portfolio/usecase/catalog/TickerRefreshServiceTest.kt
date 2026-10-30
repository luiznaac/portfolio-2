package dev.agner.portfolio.usecase.catalog

import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.catalog.gateway.CotahistFetchException
import dev.agner.portfolio.usecase.catalog.gateway.ICotahistSource
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.RefreshFailureKind
import dev.agner.portfolio.usecase.catalog.model.TickerRefreshFailure
import dev.agner.portfolio.usecase.catalog.model.UnclassifiedReason
import dev.agner.portfolio.usecase.catalog.model.UnclassifiedTicker
import dev.agner.portfolio.usecase.catalogEntry
import dev.agner.portfolio.usecase.cotahistRow
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.quotes.MonthlyCloses
import dev.agner.portfolio.usecase.quotes.QuoteService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TickerRefreshServiceTest : StringSpec({
    val catalogService = mockk<CatalogService>(relaxed = true)
    val quoteService = mockk<QuoteService>(relaxed = true)
    val source = mockk<ICotahistSource>(relaxed = true)
    val positions = mockk<IPositionRepository>(relaxed = true)
    val trades = mockk<ITradeRepository>(relaxed = true)
    val service = TickerRefreshService(catalogService, quoteService, source, positions, trades, CLOCK)

    beforeTest {
        clearAllMocks()
        coEvery { quoteService.storeMonthlyCloses(any(), any()) } returns MonthlyCloses(0, null)
        coEvery { quoteService.storeDailyCloses(any()) } returns 0
        every { source.fetchYear(any()) } returns flowOf()
    }

    "one refresh updates catalog and quotes from the same daily file and reaches the ledger's years" {
        val dailyRows = listOf(
            cotahistRow(ticker = "PETR4", date = "2026-10-21", closePrice = "48"),
            cotahistRow(ticker = "MXRF11", date = "2026-10-21", closePrice = "10"),
        )
        coEvery { source.fetchTradingDay() } returns dailyRows
        every { source.fetchYear(any()) } returns flowOf(
            cotahistRow(ticker = "PETR4", date = "2020-06-30", closePrice = "20"),
        )
        coEvery { trades.fetchAll() } returns listOf(
            buy(ticker = "PETR4", date = "2020-01-02"),
            buy(ticker = "VALE3", date = "2021-03-03"),
        )
        coEvery { positions.fetchAll() } returns listOf(position("PETR4", "10"))
        coEvery { catalogService.refresh(dailyRows) } returns 2
        coEvery { quoteService.storeDailyCloses(dailyRows) } returns 2
        coEvery { quoteService.storeMonthlyCloses(any(), setOf("PETR4", "VALE3")) } returns
            MonthlyCloses(1, LocalDate.parse("2020-06-30"))
        coEvery { quoteService.latestClose(any()) } returns BigDecimal("48")
        coEvery { catalogService.fetchAll() } returns listOf(
            catalogEntry(ticker = "PETR4", assetKind = AssetKind.STOCK, assetClass = AssetClass.ACOES),
        )

        val result = service.refresh()

        result.catalog shouldBe 2
        result.quotes shouldBe 9
        result.asOf shouldBe LocalDate.parse("2026-10-21")
        result.failed shouldBe emptyList()
        result.unclassified shouldBe emptyList()
        coVerify(exactly = 1) { source.fetchTradingDay() }
        coVerify(exactly = 1) { catalogService.refresh(dailyRows) }
        verify(exactly = 1) { source.fetchYear(2020) }
        verify(exactly = 1) { source.fetchYear(2025) }
        verify(exactly = 1) { source.fetchYear(2026) }
    }

    "does not fetch historical years when the ledger is empty" {
        coEvery { source.fetchTradingDay() } returns emptyList()
        coEvery { trades.fetchAll() } returns emptyList()
        coEvery { positions.fetchAll() } returns emptyList()
        coEvery { catalogService.fetchAll() } returns emptyList()

        val result = service.refresh()

        result.quotes shouldBe 0
        result.asOf shouldBe null
        verify(exactly = 0) { source.fetchYear(any()) }
    }

    "names each held ticker that stays classless with its reason" {
        val dailyRows = listOf(
            cotahistRow(ticker = "PETR4", date = "2026-10-21", closePrice = "48"),
            cotahistRow(ticker = "BDI3", date = "2026-10-21", closePrice = "5"),
            cotahistRow(ticker = "NEW3", date = "2026-10-21", closePrice = "7"),
        )
        coEvery { source.fetchTradingDay() } returns dailyRows
        coEvery { trades.fetchAll() } returns emptyList()
        coEvery { positions.fetchAll() } returns listOf(
            position("PETR4", "1"),
            position("BDI3", "1"),
            position("ABS3", "1"),
            position("NEW3", "1"),
        )
        coEvery { quoteService.latestClose("PETR4") } returns BigDecimal("48")
        coEvery { quoteService.latestClose("BDI3") } returns BigDecimal("5")
        coEvery { quoteService.latestClose("NEW3") } returns BigDecimal("7")
        coEvery { quoteService.latestClose("ABS3") } returns null
        coEvery { catalogService.fetchAll() } returns listOf(
            catalogEntry(ticker = "PETR4", assetKind = AssetKind.STOCK, assetClass = AssetClass.ACOES),
            catalogEntry(ticker = "BDI3", bdiType = "99", assetKind = AssetKind.UNKNOWN, assetClass = null),
            catalogEntry(ticker = "ABS3", bdiType = "99", assetKind = AssetKind.UNKNOWN, assetClass = null),
        )

        val result = service.refresh()

        result.unclassified shouldBe listOf(
            UnclassifiedTicker("ABS3", UnclassifiedReason.ABSENT_FROM_LATEST_FILE),
            UnclassifiedTicker("BDI3", UnclassifiedReason.UNHANDLED_BDI_CODE),
            UnclassifiedTicker("NEW3", UnclassifiedReason.UNHANDLED_BDI_CODE),
        )
        result.failed shouldBe listOf(TickerRefreshFailure.Ticker("ABS3", "no quote available"))
    }

    "reports the latest annual close as asOf when the daily file fails" {
        coEvery { source.fetchTradingDay() } throws CotahistFetchException("daily file missing")
        coEvery { trades.fetchAll() } returns listOf(buy(ticker = "PETR4", date = "2026-01-02"))
        coEvery { positions.fetchAll() } returns emptyList()
        coEvery { quoteService.storeMonthlyCloses(any(), any()) } returns
            MonthlyCloses(1, LocalDate.parse("2026-09-30"))
        coEvery { catalogService.fetchAll() } returns emptyList()

        val result = service.refresh()

        result.quotes shouldBe 1
        result.asOf shouldBe LocalDate.parse("2026-09-30")
        result.failed.single().kind shouldBe RefreshFailureKind.SOURCE
    }

    "a failed annual file is tagged as a source failure while the other years still load" {
        coEvery { source.fetchTradingDay() } returns listOf(
            cotahistRow(ticker = "PETR4", date = "2026-10-21", closePrice = "48"),
        )
        coEvery { trades.fetchAll() } returns listOf(buy(ticker = "PETR4", date = "2020-01-02"))
        coEvery { positions.fetchAll() } returns listOf(position("PETR4", "10"))
        coEvery { quoteService.latestClose("PETR4") } returns BigDecimal("48")
        coEvery { catalogService.fetchAll() } returns listOf(
            catalogEntry(ticker = "PETR4", assetKind = AssetKind.STOCK, assetClass = AssetClass.ACOES),
        )
        every { source.fetchYear(2020) } throws CotahistFetchException("annual file missing")

        val result = service.refresh()

        result.failed shouldBe listOf(
            TickerRefreshFailure.Source("cotahist-annual-2020", "annual file missing"),
        )
        result.failed.single().kind shouldBe RefreshFailureKind.SOURCE
        verify(exactly = 1) { source.fetchYear(2021) }
    }

    "a failed daily file is reported to the caller and does not zero the refresh silently" {
        coEvery { source.fetchTradingDay() } throws CotahistFetchException("daily file missing")
        coEvery { trades.fetchAll() } returns emptyList()
        coEvery { positions.fetchAll() } returns emptyList()
        coEvery { catalogService.fetchAll() } returns emptyList()

        val result = service.refresh()

        result.failed shouldBe listOf(TickerRefreshFailure.Source("cotahist-daily", "daily file missing"))
        result.catalog shouldBe 0
        result.quotes shouldBe 0
        result.asOf shouldBe null
        coVerify(exactly = 0) { catalogService.refresh(any()) }
    }
})

private val CLOCK = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC)

private fun position(ticker: String, quantity: String) = Position(
    ticker = ticker,
    quantity = BigDecimal(quantity),
    averagePrice = BigDecimal("10"),
)
