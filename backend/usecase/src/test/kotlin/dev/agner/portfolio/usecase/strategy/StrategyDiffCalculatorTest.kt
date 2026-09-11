package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class StrategyDiffCalculatorTest : StringSpec({
    val calculator = StrategyDiffCalculator()

    "should detect a ticker that entered" {
        val before = listOf(StrategyTarget("PETR4", BigDecimal("0.10")))
        val after = listOf(
            StrategyTarget("PETR4", BigDecimal("0.10")),
            StrategyTarget("VALE3", BigDecimal("0.05")),
        )

        val diff = calculator.diff(before, after)

        diff.entered shouldBe listOf(StrategyTarget("VALE3", BigDecimal("0.05")))
        diff.exited shouldBe emptyList()
        diff.changed shouldBe emptyList()
    }

    "should detect a ticker that exited, keeping its previous weight" {
        val before = listOf(
            StrategyTarget("PETR4", BigDecimal("0.10")),
            StrategyTarget("ORVR3", BigDecimal("0.15")),
        )
        val after = listOf(StrategyTarget("PETR4", BigDecimal("0.10")))

        val diff = calculator.diff(before, after)

        diff.entered shouldBe emptyList()
        diff.exited shouldBe listOf(StrategyTarget("ORVR3", BigDecimal("0.15")))
        diff.changed shouldBe emptyList()
    }

    "should detect a weight change for a ticker present in both editions" {
        val before = listOf(StrategyTarget("CEAB3", BigDecimal("0.10")))
        val after = listOf(StrategyTarget("CEAB3", BigDecimal("0.05")))

        val diff = calculator.diff(before, after)

        diff.entered shouldBe emptyList()
        diff.exited shouldBe emptyList()
        diff.changed.map { it.ticker } shouldBe listOf("CEAB3")
        diff.changed.first().before.weight shouldBe BigDecimal("0.10")
        diff.changed.first().after.weight shouldBe BigDecimal("0.05")
    }

    "should not report a ticker with the same weight in both editions as changed" {
        val before = listOf(StrategyTarget("PETR4", BigDecimal("0.10")))
        val after = listOf(StrategyTarget("PETR4", BigDecimal("0.10")))

        val diff = calculator.diff(before, after)

        diff.changed shouldBe emptyList()
    }

    "should detect rating and target price changes" {
        val before = listOf(StrategyTarget("PETR4", BigDecimal("0.10"), "COMPRA", BigDecimal("40.00")))
        val after = listOf(StrategyTarget("PETR4", BigDecimal("0.10"), "NEUTRO", BigDecimal("45.00")))

        calculator.diff(before, after).changed.map { it.ticker } shouldBe listOf("PETR4")
    }

    "should handle the first edition (nothing before) as everything entering" {
        val after = listOf(
            StrategyTarget("PETR4", BigDecimal("0.10")),
            StrategyTarget("VALE3", BigDecimal("0.05")),
        )

        val diff = calculator.diff(emptyList(), after)

        diff.entered shouldBe after
        diff.exited shouldBe emptyList()
        diff.changed shouldBe emptyList()
    }
})
