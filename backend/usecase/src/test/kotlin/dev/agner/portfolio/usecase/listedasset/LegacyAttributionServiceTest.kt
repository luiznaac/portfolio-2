package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlice
import dev.agner.portfolio.usecase.listedasset.model.AttributionSliceCreation
import dev.agner.portfolio.usecase.listedasset.model.AttributionSummary
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionSkipReason
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The bulk legacy action's orchestration: the reads the plan composes, the per-Ticker write through
// the attribution path, and the per-Ticker failure that must not abort the run.
class LegacyAttributionServiceTest : StringSpec({
    val positions = mockk<IPositionRepository>()
    val catalog = mockk<ICatalogRepository>()
    val strategies = mockk<IStrategyRepository>()
    val quotes = mockk<QuoteService>(relaxed = true)
    val months = mockk<MonthService>(relaxed = true)
    val attributions = mockk<AttributionService>()
    val service = LegacyAttributionService(positions, catalog, strategies, quotes, months, attributions)

    beforeTest {
        clearAllMocks()
        coEvery { positions.fetchAll() } returns emptyList()
        coEvery { catalog.fetchAll() } returns emptyList()
        coEvery { strategies.fetchAll() } returns emptyList()
        coEvery { attributions.fetchSlicesByTicker() } returns emptyMap()
        coEvery { months.fetchCurrent() } returns null
    }

    "previews the adds and the skips from the ledger and the catalog" {
        coEvery { positions.fetchAll() } returns listOf(
            position("ITUB4", "50"),
            position("VALE3", "30"),
            position("ZZZZ3", "10"),
        )
        coEvery { catalog.fetchAll() } returns listOf(
            entry("ITUB4", AssetClass.ACOES),
            entry("VALE3", AssetClass.ACOES),
            entry("ZZZZ3", null),
        )
        coEvery { strategies.fetchAll() } returns listOf(acoesBucket, reportStrategy)
        coEvery { attributions.fetchSlicesByTicker() } returns mapOf(
            "VALE3" to listOf(AttributionSlice(1, "Top Dividendos", BigDecimal("10"))),
        )
        coEvery { quotes.latestClose("ITUB4") } returns BigDecimal("10")
        coEvery { quotes.latestClose("VALE3") } returns BigDecimal("10")
        coEvery { quotes.latestClose("ZZZZ3") } returns BigDecimal("5")

        val preview = service.preview()

        preview.groups.single().strategyId shouldBe 9
        preview.groups.single().strategyName shouldBe "Legado Ações"
        preview.groups.single().adds.single().ticker shouldBe "ITUB4"
        preview.groups.single().adds.single().value.plain() shouldBe "500"
        preview.skips.map { it.ticker to it.reason } shouldBe listOf(
            "VALE3" to LegacyAttributionSkipReason.HAS_ATTRIBUTION,
            "ZZZZ3" to LegacyAttributionSkipReason.UNCLASSIFIED,
        )
    }

    "apply writes each add through the attribution write path" {
        coEvery { positions.fetchAll() } returns listOf(position("ITUB4", "50"))
        coEvery { catalog.fetchAll() } returns listOf(entry("ITUB4", AssetClass.ACOES))
        coEvery { strategies.fetchAll() } returns listOf(acoesBucket)
        coEvery { quotes.latestClose("ITUB4") } returns BigDecimal("10")
        coEvery { attributions.setSlice(any(), any()) } returns summary

        val result = service.apply()

        coVerify(exactly = 1) {
            attributions.setSlice(
                "ITUB4",
                match { it == AttributionSliceCreation(strategyId = 9, quantity = BigDecimal("50")) },
            )
        }
        result.attributed.single().ticker shouldBe "ITUB4"
        result.attributed.single().strategyName shouldBe "Legado Ações"
        result.attributed.single().value.plain() shouldBe "500"
        result.skipped.shouldBeEmpty()
    }

    "a bad ticker is reported without aborting the run" {
        coEvery { positions.fetchAll() } returns listOf(position("ITUB4", "50"), position("VALE3", "30"))
        coEvery { catalog.fetchAll() } returns listOf(
            entry("ITUB4", AssetClass.ACOES),
            entry("VALE3", AssetClass.ACOES),
        )
        coEvery { strategies.fetchAll() } returns listOf(acoesBucket)
        coEvery { attributions.setSlice("ITUB4", any()) } returns summary
        coEvery { attributions.setSlice("VALE3", any()) } throws InvalidAttributionException("bad ticker")

        val result = service.apply()

        result.attributed.single().ticker shouldBe "ITUB4"
        result.skipped.single().ticker shouldBe "VALE3"
        result.skipped.single().reason shouldBe LegacyAttributionSkipReason.WRITE_FAILED
        result.skipped.single().message shouldBe "bad ticker"
        coVerify(exactly = 1) { attributions.setSlice("VALE3", any()) }
    }

    "apply carries the preview skips into the result" {
        coEvery { positions.fetchAll() } returns listOf(position("ZZZZ3", "10"))
        coEvery { catalog.fetchAll() } returns listOf(entry("ZZZZ3", null))
        coEvery { strategies.fetchAll() } returns listOf(acoesBucket)

        val result = service.apply()

        result.attributed.shouldBeEmpty()
        result.skipped.single().reason shouldBe LegacyAttributionSkipReason.UNCLASSIFIED
        coVerify(exactly = 0) { attributions.setSlice(any(), any()) }
    }
})

private val acoesBucket = Strategy(9, "Legado Ações", AssetClass.ACOES, StrategyKind.MANUAL)
private val reportStrategy = Strategy(1, "Top Dividendos", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN)

private fun position(ticker: String, quantity: String) =
    Position(ticker = ticker, quantity = BigDecimal(quantity), averagePrice = BigDecimal("10"))

private fun entry(ticker: String, assetClass: AssetClass?) = CatalogEntry(
    ticker = ticker,
    shortName = ticker,
    bdiType = "02",
    isin = null,
    assetKind = AssetKind.STOCK,
    assetClass = assetClass,
    firstSeen = LocalDate.parse("2026-10-01"),
    lastSeen = LocalDate.parse("2026-10-01"),
)

private val summary = AttributionSummary(
    ticker = "ITUB4",
    custodyQuantity = BigDecimal("50"),
    attributedQuantity = BigDecimal("50"),
    unattributedQuantity = BigDecimal.ZERO,
    slices = emptyList(),
)
