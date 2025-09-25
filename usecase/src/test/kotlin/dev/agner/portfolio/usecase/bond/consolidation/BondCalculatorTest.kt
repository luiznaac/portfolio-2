package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BondCalculatorTest : StringSpec({

    val calculator = BondCalculator()

    "should calculate yield correctly with zero starting yield" {
        val principal = 1000.0
        val startingYield = 0.0
        val yieldPercentage = 5.0
        val context = BondCalculationContext(principal, startingYield, yieldPercentage)
        val expectedYieldedAmount = (principal + startingYield) * yieldPercentage / 100

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe principal
        result.yield shouldBe startingYield + expectedYieldedAmount
        result.statements.size shouldBe 1
        result.statements[0].shouldBeInstanceOf<BondCalculationRecord.Yield>()
        result.statements[0].amount shouldBe expectedYieldedAmount
    }

    "should calculate yield correctly with existing yield" {
        val principal = 2000.0
        val startingYield = 150.0
        val yieldPercentage = 3.5
        val context = BondCalculationContext(principal, startingYield, yieldPercentage)
        val expectedYieldedAmount = (principal + startingYield) * yieldPercentage / 100

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe principal
        result.yield shouldBe startingYield + expectedYieldedAmount
        result.statements.size shouldBe 1
        result.statements[0].shouldBeInstanceOf<BondCalculationRecord.Yield>()
        result.statements[0].amount shouldBe expectedYieldedAmount
    }

    "should handle zero yield percentage" {
        val principal = 5000.0
        val startingYield = 200.0
        val yieldPercentage = 0.0
        val context = BondCalculationContext(principal, startingYield, yieldPercentage)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe principal
        result.yield shouldBe startingYield
        result.statements.size shouldBe 1
        result.statements[0].amount shouldBe 0.0
    }

    "should handle negative yield percentage" {
        val principal = 1000.0
        val startingYield = 100.0
        val yieldPercentage = -2.0
        val context = BondCalculationContext(principal, startingYield, yieldPercentage)
        val expectedYieldedAmount = (principal + startingYield) * yieldPercentage / 100

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe principal
        result.yield shouldBe startingYield + expectedYieldedAmount
        result.statements.size shouldBe 1
        result.statements[0].amount shouldBe expectedYieldedAmount
        (result.statements[0].amount < 0) shouldBe true
    }
})
