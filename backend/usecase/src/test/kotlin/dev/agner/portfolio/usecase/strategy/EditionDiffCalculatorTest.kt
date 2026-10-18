package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.strategy.model.DiffChange
import dev.agner.portfolio.usecase.strategy.model.DiffWeight
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.Rating
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

// Worked examples come from the September 2026 XP Top Dividendos edition against the August one:
// PETR4 10%→12,5%, ALOS3 7,5%→10%, ITUB4 15%→10% (and Compra→Neutro), B3SA3 leaves, CXSE3 enters.
class EditionDiffCalculatorTest : StringSpec({
    val calculator = EditionDiffCalculator()

    fun entry(ticker: String, weight: String, rating: Rating? = Rating.BUY) =
        EditionEntry(ticker = ticker, weight = BigDecimal(weight), rating = rating)

    "a first edition reports every ticker as entered and nothing as exited" {
        val current = listOf(entry("PETR4", "10.00"), entry("VALE3", "12.50"))

        calculator.compute(previous = emptyList(), current = current) shouldBe
            dev.agner.portfolio.usecase.strategy.model.EditionDiff(
                entered = listOf(DiffWeight("PETR4", BigDecimal("10.00")), DiffWeight("VALE3", BigDecimal("12.50"))),
                exited = emptyList(),
                changed = emptyList(),
            )
    }

    "a ticker leaving the report exits with weight zero, never deleted" {
        val previous = listOf(entry("B3SA3", "10.00", Rating.NEUTRAL), entry("PETR4", "10.00"))
        val current = listOf(entry("PETR4", "10.00"))

        val diff = calculator.compute(previous, current)

        diff.exited shouldBe listOf(DiffWeight("B3SA3", BigDecimal("0.00")))
        diff.entered shouldBe emptyList()
    }

    "a ticker entering the report is entered with its new weight" {
        val previous = listOf(entry("PETR4", "10.00"))
        val current = listOf(entry("PETR4", "10.00"), entry("CXSE3", "5.00"))

        val diff = calculator.compute(previous, current)

        diff.entered shouldBe listOf(DiffWeight("CXSE3", BigDecimal("5.00")))
        diff.exited shouldBe emptyList()
        diff.changed shouldBe emptyList()
    }

    "a weight change is a change carrying both weights" {
        val previous = listOf(entry("PETR4", "10.00"))
        val current = listOf(entry("PETR4", "12.50"))

        val diff = calculator.compute(previous, current)

        diff.changed shouldBe listOf(
            DiffChange(
                ticker = "PETR4",
                weightBefore = BigDecimal("10.00"),
                weightAfter = BigDecimal("12.50"),
                ratingBefore = Rating.BUY,
                ratingAfter = Rating.BUY,
            ),
        )
    }

    "a rating-only change is a change with equal weights" {
        val previous = listOf(entry("ITUB4", "10.00", Rating.BUY))
        val current = listOf(entry("ITUB4", "10.00", Rating.NEUTRAL))

        val diff = calculator.compute(previous, current)

        diff.changed shouldBe listOf(
            DiffChange(
                ticker = "ITUB4",
                weightBefore = BigDecimal("10.00"),
                weightAfter = BigDecimal("10.00"),
                ratingBefore = Rating.BUY,
                ratingAfter = Rating.NEUTRAL,
            ),
        )
        diff.entered shouldBe emptyList()
        diff.exited shouldBe emptyList()
    }

    "an unchanged edition produces an empty diff" {
        val previous = listOf(entry("PETR4", "10.00"), entry("VALE3", "12.50", Rating.NEUTRAL))
        val current = listOf(entry("VALE3", "12.50", Rating.NEUTRAL), entry("PETR4", "10.00"))

        calculator.compute(previous, current) shouldBe
            dev.agner.portfolio.usecase.strategy.model.EditionDiff(
                entered = emptyList(),
                exited = emptyList(),
                changed = emptyList(),
            )
    }

    "the full September Top Dividendos delta classifies entered, exited and changed together" {
        val previous = listOf(
            entry("PETR4", "10.00"),
            entry("ALOS3", "7.50"),
            entry("ITUB4", "15.00", Rating.BUY),
            entry("B3SA3", "10.00", Rating.NEUTRAL),
            entry("VALE3", "12.50", Rating.NEUTRAL),
        )
        val current = listOf(
            entry("PETR4", "12.50"),
            entry("ALOS3", "10.00"),
            entry("ITUB4", "10.00", Rating.NEUTRAL),
            entry("VALE3", "12.50", Rating.NEUTRAL),
            entry("CXSE3", "5.00"),
        )

        val diff = calculator.compute(previous, current)

        diff.entered shouldBe listOf(DiffWeight("CXSE3", BigDecimal("5.00")))
        diff.exited shouldBe listOf(DiffWeight("B3SA3", BigDecimal("0.00")))
        diff.changed shouldBe listOf(
            DiffChange("PETR4", BigDecimal("10.00"), BigDecimal("12.50"), Rating.BUY, Rating.BUY),
            DiffChange("ALOS3", BigDecimal("7.50"), BigDecimal("10.00"), Rating.BUY, Rating.BUY),
            DiffChange("ITUB4", BigDecimal("15.00"), BigDecimal("10.00"), Rating.BUY, Rating.NEUTRAL),
        )
    }
})
