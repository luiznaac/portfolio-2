package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.bondCalculationContext
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.math.RoundingMode

class BondCalculatorTest : StringSpec({

    val calculator = BondCalculator()

    "should calculate yield correctly with zero starting yield" {
        val principal = BigDecimal("1000.00")
        val startingYield = BigDecimal("0.00")
        val yieldPercentage = BigDecimal("5.00")
        val context = bondCalculationContext(principal, startingYield, yieldPercentage)
        val expectedYieldedAmount = ((principal + startingYield) * yieldPercentage / BigDecimal("100")).defaultScale()

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe principal
        result.yield shouldBe startingYield + expectedYieldedAmount
        result.statements.size shouldBe 1
        result.statements[0].shouldBeInstanceOf<BondCalculationRecord.Yield>()
        result.statements[0].amount shouldBe expectedYieldedAmount
    }

    "should calculate yield correctly with existing yield" {
        val principal = BigDecimal("2000.00")
        val startingYield = BigDecimal("150.00")
        val yieldPercentage = BigDecimal("3.50")
        val context = bondCalculationContext(principal, startingYield, yieldPercentage)
        val expectedYieldedAmount = ((principal + startingYield) * yieldPercentage / BigDecimal("100")).defaultScale()

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe principal
        result.yield shouldBe startingYield + expectedYieldedAmount
        result.statements.size shouldBe 1
        result.statements[0].shouldBeInstanceOf<BondCalculationRecord.Yield>()
        result.statements[0].amount shouldBe expectedYieldedAmount
    }

    "should handle zero yield percentage" {
        val principal = BigDecimal("5000.00")
        val startingYield = BigDecimal("200.00")
        val yieldPercentage = BigDecimal("0.00")
        val context = bondCalculationContext(principal, startingYield, yieldPercentage)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe principal
        result.yield shouldBe startingYield
        result.statements.size shouldBe 0
    }

    "should calculate partial redemption with equal principal and yield proportions" {
        val principal = BigDecimal("1000.00")
        val startingYield = BigDecimal("1000.00")
        val yieldPercentage = BigDecimal("0.00")
        val redemptionAmount = BigDecimal("500.00")
        val context = bondCalculationContext(principal, startingYield, yieldPercentage, redemptionAmount)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe BigDecimal("750.00")
        result.yield shouldBe BigDecimal("750.00")
        result.statements.size shouldBe 2
        result.statements.find { it is BondCalculationRecord.PrincipalRedeem }.also {
            it!!.amount shouldBe BigDecimal("250.00")
        }
        result.statements.find { it is BondCalculationRecord.YieldRedeem }.also {
            it!!.amount shouldBe BigDecimal("250.00")
        }
    }

    "should calculate redemption with higher principal proportion" {
        val principal = BigDecimal("2000.00")
        val startingYield = BigDecimal("500.00")
        val yieldPercentage = BigDecimal("2.00")
        val redemptionAmount = BigDecimal("600.00")
        val context = bondCalculationContext(principal, startingYield, yieldPercentage, redemptionAmount)

        val totalBeforeRedemption = principal + startingYield +
            ((principal + startingYield) * yieldPercentage / BigDecimal("100")).defaultScale()
        val principalProportion = principal.setScale(6) / totalBeforeRedemption
        val expectedRedeemedPrincipal = (redemptionAmount * principalProportion).defaultScale()
        val expectedRedeemedYield = (redemptionAmount * (BigDecimal.ONE - principalProportion)).defaultScale()

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
        val principal = BigDecimal("1000.00")
        val startingYield = BigDecimal("200.00")
        val yieldPercentage = BigDecimal("5.00")
        val yieldedAmount = ((principal + startingYield) * yieldPercentage / BigDecimal("100")).setScale(
            2,
            RoundingMode.HALF_EVEN
        )
        val totalAmount = principal + startingYield + yieldedAmount
        val redemptionAmount = totalAmount
        val context = bondCalculationContext(principal, startingYield, yieldPercentage, redemptionAmount)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe BigDecimal("0.00")
        result.yield shouldBe BigDecimal("0.00")
        result.statements.size shouldBe 3
        result.statements.find { it is BondCalculationRecord.PrincipalRedeem }.also {
            it!!.amount shouldBe principal
        }
        result.statements.find { it is BondCalculationRecord.YieldRedeem }.also {
            it!!.amount shouldBe startingYield + yieldedAmount
        }
    }

    "should return RemainingRedemption when redemption amount exceeds total bond value" {
        val principal = BigDecimal("1000.00")
        val startingYield = BigDecimal("200.00")
        val yieldPercentage = BigDecimal("5.00")
        val yieldedAmount = ((principal + startingYield) * yieldPercentage / BigDecimal("100")).defaultScale()
        val totalAmount = principal + startingYield + yieldedAmount
        val redemptionAmount = totalAmount + BigDecimal("500.00") // Exceeds total by 500
        val context = bondCalculationContext(principal, startingYield, yieldPercentage, redemptionAmount)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.RemainingRedemption>()
        result.principal shouldBe BigDecimal("0.00")
        result.yield shouldBe BigDecimal("0.00")
        result.remainingRedemptionAmount shouldBe BigDecimal("500.00")
        result.statements.size shouldBe 3
        result.statements.find { it is BondCalculationRecord.PrincipalRedeem }.also {
            it!!.amount shouldBe principal
        }
        result.statements.find { it is BondCalculationRecord.YieldRedeem }.also {
            it!!.amount shouldBe startingYield + yieldedAmount
        }
    }

    "should handle taxes" {
        val principal = BigDecimal("0.00")
        val startingYield = BigDecimal("100.00")
        val yieldPercentage = BigDecimal("0.00")
        val taxes = setOf(TaxIncidence.Renda(BigDecimal("22.50")))
        val redemptionAmount = BigDecimal("55.64")
        val context = bondCalculationContext(principal, startingYield, yieldPercentage, redemptionAmount, taxes)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe BigDecimal("0.00")
        result.yield shouldBe BigDecimal("28.21")

        result.statements.find {
            it is BondCalculationRecord.TaxRedeem && it.taxType == "RENDA"
        }!!.amount shouldBe BigDecimal("16.15")
    }

    "should handle taxes - full redemption" {
        val principal = BigDecimal("1000.00")
        val startingYield = BigDecimal("100.00")
        val yieldPercentage = BigDecimal("0.00")
        val taxes = setOf(TaxIncidence.Renda(BigDecimal("22.50")), TaxIncidence.IOF(BigDecimal("90.00")))
        val redemptionAmount = BigDecimal("1007.75")
        val context = bondCalculationContext(principal, startingYield, yieldPercentage, redemptionAmount, taxes)

        val result = calculator.calculate(context)

        result.shouldBeInstanceOf<BondCalculationResult.Ok>()
        result.principal shouldBe BigDecimal("0.00")
        result.yield shouldBe BigDecimal("0.00")

        result.statements.find {
            it is BondCalculationRecord.TaxRedeem && it.taxType == "RENDA"
        }!!.amount shouldBe BigDecimal("22.50")
        result.statements.find {
            it is BondCalculationRecord.TaxRedeem && it.taxType == "IOF"
        }!!.amount shouldBe BigDecimal("69.75")
    }
})
