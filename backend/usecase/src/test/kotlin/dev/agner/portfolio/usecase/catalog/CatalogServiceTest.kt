package dev.agner.portfolio.usecase.catalog

import dev.agner.portfolio.usecase.catalog.gateway.ICotahistSource
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class CatalogServiceTest : StringSpec({
    val repository = mockk<ICatalogRepository>(relaxed = true)
    val source = mockk<ICotahistSource>(relaxed = true)
    val service = CatalogService(repository, source, CLOCK)

    beforeTest { clearAllMocks() }

    "fetchBy should look the ticker up under its normalized key" {
        coEvery { repository.fetchBy(any()) } returns null

        service.fetchBy("alup11f")

        coVerify(exactly = 1) { repository.fetchBy("ALUP11") }
    }

    "refresh should read the latest trading day from the source" {
        coEvery { source.fetchTradingDay() } returns emptyList()
        coEvery { repository.upsertAll(any()) } returns 0

        service.refresh()

        coVerify(exactly = 1) { source.fetchTradingDay() }
    }

    "refresh should derive kind and class from the BDI type and upsert the entries" {
        val rows = listOf(
            row(ticker = "PETR4", bdiType = "02"),
            row(ticker = "MXRF11", bdiType = "12"),
            row(ticker = "AAPL34", bdiType = "34"),
        )
        coEvery { source.fetchTradingDay() } returns rows
        val captured = slot<List<CatalogEntry>>()
        coEvery { repository.upsertAll(capture(captured)) } returns rows.size

        service.refresh() shouldBe rows.size

        captured.captured.map { it.ticker to it.assetKind } shouldBe listOf(
            "PETR4" to AssetKind.STOCK,
            "MXRF11" to AssetKind.FII,
            "AAPL34" to AssetKind.BDR,
        )
        captured.captured.map { it.assetClass } shouldBe listOf(
            AssetClass.ACOES,
            AssetClass.FIIS,
            AssetClass.ACOES,
        )
    }

    "refresh should keep an unclassified BDI as UNKNOWN with no class" {
        coEvery { source.fetchTradingDay() } returns listOf(row(ticker = "XPTO3", bdiType = "99"))
        val captured = slot<List<CatalogEntry>>()
        coEvery { repository.upsertAll(capture(captured)) } returns 1

        service.refresh()

        captured.captured.single().assetKind shouldBe AssetKind.UNKNOWN
        captured.captured.single().assetClass shouldBe null
    }

    "remapTicker copies the ISIN and descriptive fields into the new ticker's entry" {
        coEvery { repository.fetchBy("PETR4") } returns entry(
            ticker = "PETR4",
            isin = "BRPETRACNPR6",
            kind = AssetKind.STOCK,
            assetClass = AssetClass.ACOES,
        )
        val captured = slot<List<CatalogEntry>>()
        coEvery { repository.upsertAll(capture(captured)) } returns 1

        service.remapTicker("petr4", "petr3")

        captured.captured.size shouldBe 2
        val remapped = captured.captured.single { it.ticker == "PETR3" }
        remapped.isin shouldBe "BRPETRACNPR6"
        remapped.assetKind shouldBe AssetKind.STOCK
        remapped.assetClass shouldBe AssetClass.ACOES
        remapped.firstSeen shouldBe LocalDate.parse("2026-10-22")
        captured.captured.single { it.ticker == "PETR4" }.isin shouldBe null
    }

    "remapTicker does nothing when the old ticker is not in the catalog" {
        coEvery { repository.fetchBy("PETR4") } returns null

        service.remapTicker("PETR4", "PETR3")

        coVerify(exactly = 0) { repository.upsertAll(any()) }
    }
})

private val CLOCK = java.time.Clock.fixed(
    java.time.Instant.parse("2026-10-22T10:00:00Z"),
    java.time.ZoneOffset.UTC,
)

private fun entry(
    ticker: String,
    isin: String?,
    kind: AssetKind,
    assetClass: AssetClass?,
) = CatalogEntry(
    ticker = ticker,
    shortName = ticker,
    bdiType = "02",
    isin = isin,
    assetKind = kind,
    assetClass = assetClass,
    firstSeen = LocalDate.parse("2026-01-02"),
    lastSeen = LocalDate.parse("2026-10-01"),
)

private fun row(ticker: String, bdiType: String, date: String = "2026-10-14") = CotahistRow(
    ticker = ticker,
    shortName = ticker,
    bdiType = bdiType,
    isin = null,
    date = LocalDate.parse(date),
    closePrice = BigDecimal("10.00"),
)
