package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlice
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlicesByTicker
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionPreview
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionSkipReason
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.toLedger
import dev.agner.portfolio.usecase.order.PlacementCalculator
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

// The bulk legacy action's plan: which held Tickers enter their class's Manual Strategy, which are
// skipped and why, and what each bucket slice will hold after apply. The whole rule lives in the
// pure planner, so the skip matrix and the absorb-both-ways behaviour are tested without a database.
class LegacyAttributionPlannerTest : StringSpec({

    "adds an outside classified ticker's whole custody to its class bucket" {
        val preview = plan(
            positions = listOf(position("ITUB4", "50")),
            classByTicker = mapOf("ITUB4" to AssetClass.ACOES),
            slicesByTicker = emptyMap(),
            prices = mapOf("ITUB4" to BigDecimal("10")),
        )

        preview.skips.shouldBeEmpty()
        preview.groups.single().assetClass shouldBe AssetClass.ACOES
        preview.groups.single().strategyName shouldBe "Legado Ações"
        preview.groups.single().adds.single().ticker shouldBe "ITUB4"
        preview.groups.single().adds.single().quantity.plain() shouldBe "50"
        preview.groups.single().adds.single().value.plain() shouldBe "500"
    }

    "skips a ticker that already carries a report attribution, even with unattributed custody" {
        val preview = plan(
            positions = listOf(position("ITUB4", "50")),
            classByTicker = mapOf("ITUB4" to AssetClass.ACOES),
            slicesByTicker = mapOf("ITUB4" to listOf(slice(reportStrategy, "20"))),
        )

        preview.groups.shouldBeEmpty()
        preview.skips.single().ticker shouldBe "ITUB4"
        preview.skips.single().reason shouldBe LegacyAttributionSkipReason.HAS_ATTRIBUTION
        preview.skips.single().assetClass shouldBe AssetClass.ACOES
    }

    "skips an unclassified ticker and reports it" {
        val preview = plan(
            positions = listOf(position("ZZZZ3", "10")),
            classByTicker = mapOf("ZZZZ3" to null),
            slicesByTicker = emptyMap(),
        )

        preview.groups.shouldBeEmpty()
        preview.skips.single().ticker shouldBe "ZZZZ3"
        preview.skips.single().reason shouldBe LegacyAttributionSkipReason.UNCLASSIFIED
        preview.skips.single().assetClass shouldBe null
    }

    "skips a class whose outside custody has no bucket" {
        val preview = plan(
            positions = listOf(position("HGLG11", "10")),
            classByTicker = mapOf("HGLG11" to AssetClass.FIIS),
            slicesByTicker = emptyMap(),
            manual = listOf(acoesBucket),
        )

        preview.groups.shouldBeEmpty()
        preview.skips.single().ticker shouldBe "HGLG11"
        preview.skips.single().reason shouldBe LegacyAttributionSkipReason.NO_BUCKET
        preview.skips.single().assetClass shouldBe AssetClass.FIIS
    }

    "absorbs a buy into a ticker already in the bucket" {
        val preview = plan(
            positions = listOf(position("ITUB4", "150")),
            classByTicker = mapOf("ITUB4" to AssetClass.ACOES),
            slicesByTicker = mapOf("ITUB4" to listOf(slice(acoesBucket, "100"))),
            prices = mapOf("ITUB4" to BigDecimal("10")),
        )

        preview.skips.shouldBeEmpty()
        preview.groups.single().adds.single().quantity.plain() shouldBe "150"
        preview.groups.single().adds.single().value.plain() shouldBe "1500"
    }

    "shrinks a bucket ticker on a sale" {
        val preview = plan(
            positions = listOf(position("ITUB4", "60")),
            classByTicker = mapOf("ITUB4" to AssetClass.ACOES),
            slicesByTicker = mapOf("ITUB4" to listOf(slice(acoesBucket, "100"))),
        )

        preview.skips.shouldBeEmpty()
        preview.groups.single().adds.single().quantity.plain() shouldBe "60"
    }

    "is idempotent: a reconciled bucket ticker is not listed again" {
        val preview = plan(
            positions = listOf(position("ITUB4", "100")),
            classByTicker = mapOf("ITUB4" to AssetClass.ACOES),
            slicesByTicker = mapOf("ITUB4" to listOf(slice(acoesBucket, "100"))),
        )

        preview.groups.shouldBeEmpty()
        preview.skips.shouldBeEmpty()
    }

    "does not touch a ticker a Target names" {
        val preview = plan(
            positions = listOf(position("ITUB4", "100")),
            classByTicker = mapOf("ITUB4" to AssetClass.ACOES),
            slicesByTicker = emptyMap(),
            targeted = setOf("ITUB4"),
        )

        preview.groups.shouldBeEmpty()
        preview.skips.shouldBeEmpty()
    }

    "a targeted ticker already in the bucket still absorbs the custody delta" {
        val preview = plan(
            positions = listOf(position("ITUB4", "150")),
            classByTicker = mapOf("ITUB4" to AssetClass.ACOES),
            slicesByTicker = mapOf("ITUB4" to listOf(slice(acoesBucket, "100"))),
            targeted = setOf("ITUB4"),
        )

        preview.groups.single().adds.single().quantity.plain() shouldBe "150"
    }

    "skips a ticker that has both a report slice and a bucket slice" {
        val preview = plan(
            positions = listOf(position("ITUB4", "150")),
            classByTicker = mapOf("ITUB4" to AssetClass.ACOES),
            slicesByTicker = mapOf(
                "ITUB4" to listOf(
                    slice(reportStrategy, "100"),
                    slice(acoesBucket, "20"),
                ),
            ),
        )

        preview.groups.shouldBeEmpty()
        preview.skips.single().reason shouldBe LegacyAttributionSkipReason.HAS_ATTRIBUTION
    }

    "groups the adds per class and orders each group by ticker" {
        val preview = plan(
            positions = listOf(
                position("VALE3", "10"),
                position("ITUB4", "50"),
                position("HGLG11", "5"),
            ),
            classByTicker = mapOf(
                "VALE3" to AssetClass.ACOES,
                "ITUB4" to AssetClass.ACOES,
                "HGLG11" to AssetClass.FIIS,
            ),
            slicesByTicker = emptyMap(),
        )

        preview.groups.map { it.assetClass } shouldBe listOf(AssetClass.ACOES, AssetClass.FIIS)
        preview.groups.first().adds.map { it.ticker } shouldBe listOf("ITUB4", "VALE3")
        preview.groups.last().adds.single().ticker shouldBe "HGLG11"
    }

    "values a ticker with no stored close at zero" {
        val preview = plan(
            positions = listOf(position("ITUB4", "50")),
            classByTicker = mapOf("ITUB4" to AssetClass.ACOES),
            slicesByTicker = emptyMap(),
            prices = emptyMap(),
        )

        preview.groups.single().adds.single().value.plain() shouldBe "0"
    }

    "ignores a position with no custody" {
        val preview = plan(
            positions = listOf(position("ITUB4", "0")),
            classByTicker = mapOf("ITUB4" to AssetClass.ACOES),
            slicesByTicker = emptyMap(),
        )

        preview.groups.shouldBeEmpty()
        preview.skips.shouldBeEmpty()
    }
})

private fun plan(
    positions: List<Position>,
    classByTicker: Map<String, AssetClass?>,
    slicesByTicker: AttributionSlicesByTicker,
    manual: List<Strategy> = listOf(acoesBucket, fiisBucket),
    targeted: Set<String> = emptySet(),
    prices: Map<String, BigDecimal> = emptyMap(),
): LegacyAttributionPreview {
    val placement = PlacementCalculator.compute(
        positions = positions.associate { it.ticker to it.quantity },
        targetedTickers = targeted,
        ledger = slicesByTicker.toLedger(),
        manualStrategies = manual.map { it.name }.toSet(),
        unclassifiedTickers = classByTicker.filterValues { it == null }.keys,
    )
    return LegacyAttributionPlanner.plan(
        positions = positions,
        classByTicker = classByTicker,
        placement = placement,
        slicesByTicker = slicesByTicker,
        manualByClass = manual.associateBy { it.assetClass },
        prices = prices,
    )
}

private val acoesBucket = Strategy(9, "Legado Ações", AssetClass.ACOES, StrategyKind.MANUAL)
private val fiisBucket = Strategy(10, "Legado FIIs", AssetClass.FIIS, StrategyKind.MANUAL)
private val reportStrategy = Strategy(1, "Top Dividendos", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN)

private fun position(ticker: String, quantity: String) =
    Position(ticker = ticker, quantity = BigDecimal(quantity), averagePrice = BigDecimal("10"))

private fun slice(strategy: Strategy, quantity: String) =
    AttributionSlice(strategyId = strategy.id, strategyName = strategy.name, quantity = BigDecimal(quantity))
