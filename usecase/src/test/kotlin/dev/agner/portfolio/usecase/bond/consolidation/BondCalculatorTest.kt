package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.bondCalculationContext
import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.math.abs

class BondCalculatorTest : StringSpec({

    val calculator = BondCalculator()

    "should calculate yield correctly with zero starting yield" {
        val principal = 1000.0
        val startingYield = 0.0
        val yieldPercentage = 5.0
        val context = bondCalculationContext(principal, startingYield, yieldPercentage)
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
        val context = bondCalculationContext(principal, startingYield, yieldPercentage)
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
        val context = bondCalculationContext(principal, startingYield, yieldPercentage)

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
        val context = bondCalculationContext(principal, startingYield, yieldPercentage)
        val expectedYieldedAmount = (principal + startingYield) * yieldPercentage / 100

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe principal
        result.yield shouldBe startingYield + expectedYieldedAmount
        result.statements.size shouldBe 1
        result.statements[0].amount shouldBe expectedYieldedAmount
        (result.statements[0].amount < 0) shouldBe true
    }

    "should calculate partial redemption with equal principal and yield proportions" {
        val principal = 1000.0
        val startingYield = 1000.0
        val yieldPercentage = 0.0
        val redemptionAmount = 500.0
        val context = bondCalculationContext(principal, startingYield, yieldPercentage, redemptionAmount)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe 750.0
        result.yield shouldBe 750.0
        result.statements.size shouldBe 3
        result.statements.find { it is BondCalculationRecord.PrincipalRedeem }.also {
            it!!.amount shouldBe 250
        }
        result.statements.find { it is BondCalculationRecord.YieldRedeem }.also {
            it!!.amount shouldBe 250
        }
    }

    "should calculate redemption with higher principal proportion" {
        val principal = 2000.0
        val startingYield = 500.0
        val yieldPercentage = 2.0
        val redemptionAmount = 600.0
        val context = bondCalculationContext(principal, startingYield, yieldPercentage, redemptionAmount)

        val totalBeforeRedemption = principal + startingYield + ((principal + startingYield) * yieldPercentage / 100)
        val principalProportion = principal / totalBeforeRedemption
        val expectedRedeemedPrincipal = redemptionAmount * principalProportion
        val expectedRedeemedYield = redemptionAmount * (1 - principalProportion)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe (principal - expectedRedeemedPrincipal)
        result.statements.size shouldBe 3
        result.statements.find { it is BondCalculationRecord.PrincipalRedeem }.also {
            it!!.amount shouldBe expectedRedeemedPrincipal
        }
        result.statements.find { it is BondCalculationRecord.YieldRedeem }.also {
            it!!.amount shouldBe expectedRedeemedYield
        }
    }

    "should calculate full redemption" {
        val principal = 1000.0
        val startingYield = 200.0
        val yieldPercentage = 5.0
        val yieldedAmount = (principal + startingYield) * yieldPercentage / 100
        val totalAmount = principal + startingYield + yieldedAmount
        val redemptionAmount = totalAmount
        val context = bondCalculationContext(principal, startingYield, yieldPercentage, redemptionAmount)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe 0.0
        result.yield shouldBe 0.0
        result.statements.size shouldBe 3
        result.statements.find { it is BondCalculationRecord.PrincipalRedeem }.also {
            it!!.amount shouldBe principal
        }
        result.statements.find { it is BondCalculationRecord.YieldRedeem }.also {
            it!!.amount.shouldBeEqualToWithDelta(startingYield + yieldedAmount, 0.001)
        }
    }

    "should return RemainingRedemption when redemption amount exceeds total bond value" {
        val principal = 1000.0
        val startingYield = 200.0
        val yieldPercentage = 5.0
        val yieldedAmount = (principal + startingYield) * yieldPercentage / 100
        val totalAmount = principal + startingYield + yieldedAmount
        val redemptionAmount = totalAmount + 500.0 // Exceeds total by 500
        val context = bondCalculationContext(principal, startingYield, yieldPercentage, redemptionAmount)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.RemainingRedemption>()
        result.principal shouldBe 0.0
        result.yield shouldBe 0.0
        result.remainingRedemptionAmount shouldBe 500.0
        result.statements.size shouldBe 3
        result.statements.find { it is BondCalculationRecord.PrincipalRedeem }.also {
            it!!.amount shouldBe principal
        }
        result.statements.find { it is BondCalculationRecord.YieldRedeem }.also {
            it!!.amount.shouldBeEqualToWithDelta(startingYield + yieldedAmount, 0.001)
        }
    }

    "should handle taxes" {
        val principal = 0.0
        val startingYield = 100.0
        val yieldPercentage = 0.0
        val taxes = setOf(TaxIncidence.Renda(25.0), TaxIncidence.IOF(90.0))
        val redemptionAmount = 7.5
        val context = bondCalculationContext(principal, startingYield, yieldPercentage, redemptionAmount, taxes)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe 0.0
        result.yield.shouldBeEqualToWithDelta(0.0, 0.01)

        result.statements.find {
            it is BondCalculationRecord.TaxRedeem && it.taxType == "IOF"
        }!!.amount.shouldBeEqualToWithDelta(67.5, 0.01)
        result.statements.find {
            it is BondCalculationRecord.TaxRedeem && it.taxType == "RENDA"
        }!!.amount.shouldBeEqualToWithDelta(25.0, 0.01)
    }
})

private fun Double.shouldBeEqualToWithDelta(other: Double, delta: Double) = abs(this - other) shouldBeLessThan delta
