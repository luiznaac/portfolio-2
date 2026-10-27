package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.order.model.Placement
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

// The one inside-a-Strategy seam: a held ticker is INSIDE when a Target names it with weight > 0 or
// a Manual Strategy holds at least one of its units; a report-driven Attribution alone is inert. A
// null effective class is its own data-quality state, never an outside holding. No repository
// reaches this seam.
class PlacementCalculatorTest : StringSpec({

    "places a target-only ticker inside with no manual units" {
        val placement = compute(
            positions = mapOf("VALE3" to "100"),
            targetedTickers = setOf("VALE3"),
        )

        placement.getValue("VALE3").placement shouldBe Placement.INSIDE
        placement.getValue("VALE3").manualQuantity.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    "places a manual-attribution-only ticker inside" {
        val placement = compute(
            positions = mapOf("MXRF11" to "25"),
            ledger = mapOf(StrategyTicker("Legado", "MXRF11") to BigDecimal("25")),
            manualStrategies = setOf("Legado"),
        )

        placement.getValue("MXRF11").placement shouldBe Placement.INSIDE
        placement.getValue("MXRF11").manualQuantity.compareTo(BigDecimal("25")) shouldBe 0
    }

    "keeps a target-and-manual ticker inside with the whole position as custody" {
        val placement = compute(
            positions = mapOf("VALE3" to "100"),
            targetedTickers = setOf("VALE3"),
            ledger = mapOf(StrategyTicker("Legado", "VALE3") to BigDecimal("30")),
            manualStrategies = setOf("Legado"),
        )

        placement.getValue("VALE3").placement shouldBe Placement.INSIDE
        placement.getValue("VALE3").manualQuantity.compareTo(BigDecimal("30")) shouldBe 0
    }

    "places a classified ticker with no target and no manual units outside" {
        val placement = compute(
            positions = mapOf("MXRF11" to "25"),
            targetedTickers = setOf("VALE3"),
        )

        placement.getValue("MXRF11").placement shouldBe Placement.OUTSIDE
    }

    "treats a report-driven attribution as inert" {
        val placement = compute(
            positions = mapOf("MXRF11" to "25"),
            targetedTickers = setOf("VALE3"),
            ledger = mapOf(StrategyTicker("Top", "MXRF11") to BigDecimal("25")),
            manualStrategies = setOf("Legado"),
        )

        placement.getValue("MXRF11").placement shouldBe Placement.OUTSIDE
        placement.getValue("MXRF11").manualQuantity.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    "keeps an unclassified ticker out of every Strategy even when a Target names it" {
        val placement = compute(
            positions = mapOf("PETR4" to "10"),
            targetedTickers = setOf("PETR4"),
            unclassifiedTickers = setOf("PETR4"),
        )

        placement.getValue("PETR4").placement shouldBe Placement.UNCLASSIFIED
    }

    "caps a stale manual ledger at the custody the book still holds" {
        val placement = compute(
            positions = mapOf("MXRF11" to "100"),
            ledger = mapOf(StrategyTicker("Legado", "MXRF11") to BigDecimal("150")),
            manualStrategies = setOf("Legado"),
        )

        placement.getValue("MXRF11").placement shouldBe Placement.INSIDE
        placement.getValue("MXRF11").manualQuantity.compareTo(BigDecimal("100")) shouldBe 0
    }
})

private fun compute(
    positions: Map<String, String>,
    targetedTickers: Set<String> = emptySet(),
    ledger: Map<StrategyTicker, BigDecimal> = emptyMap(),
    manualStrategies: Set<String> = emptySet(),
    unclassifiedTickers: Set<String> = emptySet(),
) = PlacementCalculator.compute(
    positions = positions.mapValues { BigDecimal(it.value) },
    targetedTickers = targetedTickers,
    ledger = ledger,
    manualStrategies = manualStrategies,
    unclassifiedTickers = unclassifiedTickers,
)
