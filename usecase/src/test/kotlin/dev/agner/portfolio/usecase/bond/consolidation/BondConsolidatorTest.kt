
package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bondCalculationContext
import dev.agner.portfolio.usecase.bondConsolidationContext
import dev.agner.portfolio.usecase.iofIncidence
import dev.agner.portfolio.usecase.rendaIncidence
import dev.agner.portfolio.usecase.tax.TaxService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class BondConsolidatorTest : StringSpec({

    val calculator = mockk<BondCalculator>()
    val taxService = mockk<TaxService>(relaxed = true)
    val service = BondConsolidator(calculator, taxService)

    beforeEach { clearAllMocks() }

    "should process bond consolidation with yield and redemptions correctly" {
        val bondOrderId = 1
        val date1 = LocalDate.parse("2024-01-16")
        val date2 = LocalDate.parse("2024-01-17")

        val consolidationContext = bondConsolidationContext(
            bondOrderId = bondOrderId,
            principal = 10000.0,
            yieldAmount = 0.0,
            yieldPercentages = mapOf(
                date1 to BondConsolidationContext.YieldPercentageContext(0.5),
                date2 to BondConsolidationContext.YieldPercentageContext(0.6)
            ),
            sellOrders = mapOf(
                date1 to BondConsolidationContext.SellOrderContext(5, 500.0),
                date2 to BondConsolidationContext.SellOrderContext(7, 500.0)
            )
        )

        val taxes1 = setOf(iofIncidence(), rendaIncidence())
        val taxes2 = setOf(iofIncidence(), rendaIncidence())

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = 10000.0,
                    startingYield = 0.0,
                    yieldPercentage = 0.5,
                    sellAmount = 500.0,
                    taxes = taxes1,
                )
            )
        } returns BondCalculationResult.Ok(
            principal = 9500.0,
            yield = 100.0,
            statements = listOf(
                BondCalculationRecord.Yield(100.0),
                BondCalculationRecord.PrincipalRedeem(500.0)
            )
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = 9500.0,
                    startingYield = 100.0,
                    yieldPercentage = 0.6,
                    sellAmount = 500.0,
                    taxes = taxes2,
                )
            )
        } returns BondCalculationResult.Ok(
            principal = 9000.0,
            yield = 160.0,
            statements = listOf(
                BondCalculationRecord.Yield(60.0),
                BondCalculationRecord.YieldRedeem(100.0),
                BondCalculationRecord.PrincipalRedeem(400.0)
            )
        )

        every { taxService.getTaxIncidencesBy(date1, consolidationContext.contributionDate) } returns taxes1
        every { taxService.getTaxIncidencesBy(date2, consolidationContext.contributionDate) } returns taxes2

        val result = service.calculateBondo(consolidationContext)

        result.remainingSells shouldBe emptyMap()
        result.statements shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, date1, 100.0),
            BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date1, 500.0, 5),
            BondOrderStatementCreation.Yield(bondOrderId, date2, 60.0),
            BondOrderStatementCreation.YieldRedeem(bondOrderId, date2, 100.0, 7),
            BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date2, 400.0, 7)
        )

        coVerify(exactly = 2) { calculator.calculate(any()) }
    }

    "should handle remaining redemption when sell amount is larger than available" {
        val bondOrderId = 1
        val date1 = LocalDate.parse("2024-01-16")

        val consolidationContext = bondConsolidationContext(
            bondOrderId = bondOrderId,
            principal = 1000.0,
            yieldAmount = 0.0,
            yieldPercentages = mapOf(
                date1 to BondConsolidationContext.YieldPercentageContext(0.5)
            ),
            sellOrders = mapOf(
                date1 to BondConsolidationContext.SellOrderContext(5, 1500.0)
            )
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = 1000.0,
                    startingYield = 0.0,
                    yieldPercentage = 0.5,
                    sellAmount = 1500.0
                )
            )
        } returns BondCalculationResult.RemainingRedemption(
            principal = 0.0,
            yield = 5.0,
            statements = listOf(
                BondCalculationRecord.Yield(5.0),
                BondCalculationRecord.PrincipalRedeem(1000.0)
            ),
            remainingRedemptionAmount = 500.0
        )

        val result = service.calculateBondo(consolidationContext)

        result.remainingSells shouldBe mapOf(
            date1 to BondConsolidationContext.SellOrderContext(5, 500.0)
        )
        result.statements shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, date1, 5.0),
            BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date1, 1000.0, 5)
        )

        coVerify(exactly = 1) { calculator.calculate(any()) }
    }

    "should process multiple dates in sorted order" {
        val bondOrderId = 1
        val date1 = LocalDate.parse("2024-01-20")
        val date2 = LocalDate.parse("2024-01-15") // Earlier date but added later
        val date3 = LocalDate.parse("2024-01-25")

        val consolidationContext = bondConsolidationContext(
            bondOrderId = bondOrderId,
            principal = 10000.0,
            yieldAmount = 0.0,
            yieldPercentages = mapOf(
                date1 to BondConsolidationContext.YieldPercentageContext(0.5),
                date2 to BondConsolidationContext.YieldPercentageContext(0.4),
                date3 to BondConsolidationContext.YieldPercentageContext(0.6)
            )
        )

        // Mock responses for each calculation in chronological order
        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = 10000.0,
                    startingYield = 0.0,
                    yieldPercentage = 0.4, // date2 comes first
                    sellAmount = 0.0
                )
            )
        } returns BondCalculationResult.Ok(
            principal = 10000.0,
            yield = 40.0,
            statements = listOf(BondCalculationRecord.Yield(40.0))
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = 10000.0,
                    startingYield = 40.0,
                    yieldPercentage = 0.5, // date1 comes second
                    sellAmount = 0.0
                )
            )
        } returns BondCalculationResult.Ok(
            principal = 10000.0,
            yield = 90.0,
            statements = listOf(BondCalculationRecord.Yield(50.0))
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = 10000.0,
                    startingYield = 90.0,
                    yieldPercentage = 0.6, // date3 comes third
                    sellAmount = 0.0
                )
            )
        } returns BondCalculationResult.Ok(
            principal = 10000.0,
            yield = 150.0,
            statements = listOf(BondCalculationRecord.Yield(60.0))
        )

        val result = service.calculateBondo(consolidationContext)

        result.statements shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, date2, 40.0),
            BondOrderStatementCreation.Yield(bondOrderId, date1, 50.0),
            BondOrderStatementCreation.Yield(bondOrderId, date3, 60.0)
        )

        coVerify(exactly = 3) { calculator.calculate(any()) }
    }

    "should handle empty yield percentages" {
        val consolidationContext = bondConsolidationContext(
            bondOrderId = 1,
            principal = 10000.0,
            yieldAmount = 0.0,
            yieldPercentages = emptyMap()
        )

        val result = service.calculateBondo(consolidationContext)

        result.remainingSells shouldBe emptyMap()
        result.statements shouldBe emptyList()

        coVerify(exactly = 0) { calculator.calculate(any()) }
    }

    "should stop processing when principal and yield amount reach zero" {
        val bondOrderId = 1
        val date1 = LocalDate.parse("2024-01-16")
        val date2 = LocalDate.parse("2024-01-17")
        val date3 = LocalDate.parse("2024-01-18") // This date should not be processed

        val consolidationContext = bondConsolidationContext(
            bondOrderId = bondOrderId,
            principal = 1000.0,
            yieldAmount = 500.0,
            yieldPercentages = mapOf(
                date1 to BondConsolidationContext.YieldPercentageContext(0.5),
                date2 to BondConsolidationContext.YieldPercentageContext(0.6),
                date3 to BondConsolidationContext.YieldPercentageContext(0.7) // Should not be processed
            ),
            sellOrders = mapOf(
                date1 to BondConsolidationContext.SellOrderContext(5, 800.0),
                date2 to BondConsolidationContext.SellOrderContext(6, 900.0)
            )
        )

        // First calculation: reduces principal and yield but doesn't reach zero
        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = 1000.0,
                    startingYield = 500.0,
                    yieldPercentage = 0.5,
                    sellAmount = 800.0
                )
            )
        } returns BondCalculationResult.Ok(
            principal = 200.0,
            yield = 300.0,
            statements = listOf(
                BondCalculationRecord.Yield(50.0),
                BondCalculationRecord.YieldRedeem(250.0),
                BondCalculationRecord.PrincipalRedeem(500.0)
            )
        )

        // Second calculation: reduces both principal and yield to zero
        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = 200.0,
                    startingYield = 300.0,
                    yieldPercentage = 0.6,
                    sellAmount = 900.0
                )
            )
        } returns BondCalculationResult.RemainingRedemption(
            principal = 0.0,
            yield = 0.0, // Both reach zero - should stop processing here
            statements = listOf(
                BondCalculationRecord.Yield(20.0),
                BondCalculationRecord.YieldRedeem(320.0),
                BondCalculationRecord.PrincipalRedeem(200.0)
            ),
            remainingRedemptionAmount = 200.0,
        )

        val result = service.calculateBondo(consolidationContext)

        result.statements shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, date1, 50.0),
            BondOrderStatementCreation.YieldRedeem(bondOrderId, date1, 250.0, 5),
            BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date1, 500.0, 5),
            BondOrderStatementCreation.Yield(bondOrderId, date2, 20.0),
            BondOrderStatementCreation.YieldRedeem(bondOrderId, date2, 320.0, 6),
            BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date2, 200.0, 6)
        )

        // The remaining sell orders should include the unprocessed date2 sell order
        result.remainingSells shouldBe mapOf(
            date2 to BondConsolidationContext.SellOrderContext(6, 200.0)
        )

        // Should only call calculator twice (date1 and date2), not for date3
        coVerify(exactly = 2) { calculator.calculate(any()) }
    }
})
