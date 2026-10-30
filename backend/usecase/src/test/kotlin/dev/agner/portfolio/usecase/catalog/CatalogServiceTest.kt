package dev.agner.portfolio.usecase.catalog

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.model.effectiveAssetClass
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.catalogEntry
import dev.agner.portfolio.usecase.cotahistRow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate

class CatalogServiceTest : StringSpec({
    val repository = mockk<ICatalogRepository>(relaxed = true)
    val service = CatalogService(repository, CLOCK)

    beforeTest { clearAllMocks() }

    "fetchBy should look the ticker up under its normalized key" {
        coEvery { repository.fetchBy(any()) } returns null

        service.fetchBy("alup11f")

        coVerify(exactly = 1) { repository.fetchBy("ALUP11") }
    }

    "refresh should derive kind and class from the BDI type and upsert the entries" {
        val rows = listOf(
            cotahistRow(ticker = "PETR4", bdiType = "02"),
            cotahistRow(ticker = "MXRF11", bdiType = "12"),
            cotahistRow(ticker = "AAPL34", bdiType = "34"),
        )
        val captured = slot<List<CatalogEntry>>()
        coEvery { repository.upsertAll(capture(captured)) } returns rows.size

        service.refresh(rows) shouldBe rows.size

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
        val captured = slot<List<CatalogEntry>>()
        coEvery { repository.upsertAll(capture(captured)) } returns 1

        service.refresh(listOf(cotahistRow(ticker = "XPTO3", bdiType = "99")))

        captured.captured.single().assetKind shouldBe AssetKind.UNKNOWN
        captured.captured.single().assetClass shouldBe null
    }

    "fetchUnclassified reports the tickers with no class, including a missing row" {
        coEvery { repository.fetchAll() } returns listOf(
            catalogEntry(ticker = "PETR4", isin = null, assetKind = AssetKind.STOCK, assetClass = AssetClass.ACOES),
            catalogEntry(ticker = "XPTO3", isin = null, assetKind = AssetKind.UNKNOWN, assetClass = null),
        )

        service.fetchUnclassified(setOf("PETR4", "XPTO3", "MISS3")) shouldBe setOf("XPTO3", "MISS3")
    }

    "fetchUnclassified counts a manually classified ticker as classified" {
        coEvery { repository.fetchAll() } returns listOf(
            catalogEntry(
                ticker = "XPTO3",
                isin = null,
                assetKind = AssetKind.UNKNOWN,
                assetClass = null,
                assetClassOverride = AssetClass.FIIS,
            ),
        )

        service.fetchUnclassified(setOf("XPTO3")) shouldBe emptySet()
    }

    "refresh moves the BDI class forward but keeps the manual override" {
        coEvery { repository.fetchAll() } returns listOf(
            catalogEntry(
                ticker = "XPTO3",
                isin = null,
                assetKind = AssetKind.UNKNOWN,
                assetClass = null,
                assetClassOverride = AssetClass.ACOES,
            ),
        )
        val captured = slot<List<CatalogEntry>>()
        coEvery { repository.upsertAll(capture(captured)) } returns 1

        service.refresh(listOf(cotahistRow(ticker = "XPTO3", bdiType = "12")))

        captured.captured.single().assetClass shouldBe AssetClass.FIIS
        captured.captured.single().assetClassOverride shouldBe AssetClass.ACOES
        captured.captured.single().effectiveAssetClass shouldBe AssetClass.ACOES
    }

    "classify sets the override on the catalog row" {
        coEvery { repository.fetchBy("XPTO3") } returns catalogEntry(
            ticker = "XPTO3",
            isin = null,
            assetKind = AssetKind.UNKNOWN,
            assetClass = null,
        )
        coEvery { repository.setAssetClassOverride("XPTO3", AssetClass.ACOES) } returns catalogEntry(
            ticker = "XPTO3",
            isin = null,
            assetKind = AssetKind.UNKNOWN,
            assetClass = null,
            assetClassOverride = AssetClass.ACOES,
        )

        service.classify("xpto3", AssetClass.ACOES)?.effectiveAssetClass shouldBe AssetClass.ACOES

        coVerify(exactly = 1) { repository.setAssetClassOverride("XPTO3", AssetClass.ACOES) }
    }

    "classify auto-enters a ticker the catalog has never seen" {
        coEvery { repository.fetchBy("NOVA3") } returns null
        coEvery { repository.setAssetClassOverride("NOVA3", AssetClass.FIIS) } returns catalogEntry(
            ticker = "NOVA3",
            isin = null,
            assetKind = AssetKind.UNKNOWN,
            assetClass = null,
            assetClassOverride = AssetClass.FIIS,
        )

        service.classify("NOVA3", AssetClass.FIIS)?.effectiveAssetClass shouldBe AssetClass.FIIS

        coVerify(exactly = 1) { repository.upsertAll(any()) }
        coVerify(exactly = 1) { repository.setAssetClassOverride("NOVA3", AssetClass.FIIS) }
    }

    "classify rejects a class a listed ticker cannot hold" {
        coEvery { repository.fetchBy("XPTO3") } returns catalogEntry(
            ticker = "XPTO3",
            isin = null,
            assetKind = AssetKind.UNKNOWN,
            assetClass = null,
        )

        shouldThrow<InvalidCatalogClassException> {
            service.classify("XPTO3", AssetClass.RENDA_FIXA)
        }

        coVerify(exactly = 0) { repository.setAssetClassOverride(any(), any()) }
    }

    "revert clears the override, leaving the BDI-derived class in charge" {
        coEvery { repository.setAssetClassOverride("XPTO3", null) } returns catalogEntry(
            ticker = "XPTO3",
            isin = null,
            assetKind = AssetKind.UNKNOWN,
            assetClass = null,
        )

        service.revertClassification("xpto3")

        coVerify(exactly = 1) { repository.setAssetClassOverride("XPTO3", null) }
    }

    "remapTicker copies the ISIN and descriptive fields into the new ticker's entry" {
        coEvery { repository.fetchBy("PETR4") } returns catalogEntry(
            ticker = "PETR4",
            isin = "BRPETRACNPR6",
            assetKind = AssetKind.STOCK,
            assetClass = AssetClass.ACOES,
            assetClassOverride = AssetClass.FIIS,
        )
        val captured = slot<List<CatalogEntry>>()
        coEvery { repository.upsertAll(capture(captured)) } returns 1

        service.remapTicker("petr4", "petr3")

        captured.captured.size shouldBe 2
        val remapped = captured.captured.single { it.ticker == "PETR3" }
        remapped.isin shouldBe "BRPETRACNPR6"
        remapped.assetKind shouldBe AssetKind.STOCK
        remapped.assetClass shouldBe AssetClass.ACOES
        remapped.assetClassOverride shouldBe AssetClass.FIIS
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
