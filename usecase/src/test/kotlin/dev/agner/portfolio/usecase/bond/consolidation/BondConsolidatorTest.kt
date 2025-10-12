
package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.FullRedemptionContext
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bondCalculationContext
import dev.agner.portfolio.usecase.bondConsolidationContext
import dev.agner.portfolio.usecase.bondMaturityConsolidationContext
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
import java.math.BigDecimal

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
            dateRange = listOf(date1, date2),
            principal = BigDecimal("10000.00"),
            yieldAmount = BigDecimal("0.00"),
            yieldPercentages = mapOf(
                date1 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.50")),
                date2 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.60"))
            ),
            sellOrders = mapOf(
                date1 to BondConsolidationContext.SellOrderContext(5, BigDecimal("500.00")),
                date2 to BondConsolidationContext.SellOrderContext(7, BigDecimal("500.00"))
            )
        )

        val taxes1 = setOf(iofIncidence(), rendaIncidence())
        val taxes2 = setOf(iofIncidence(), rendaIncidence())

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("10000.00"),
                    startingYield = BigDecimal("0.00"),
                    yieldPercentage = BigDecimal("0.50"),
                    sellAmount = BigDecimal("500.00"),
                    taxes = taxes1,
                )
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("9500.00"),
            yield = BigDecimal("100.00"),
            statements = listOf(
                BondCalculationRecord.Yield(BigDecimal("100.00")),
                BondCalculationRecord.PrincipalRedeem(BigDecimal("500.00")),
                BondCalculationRecord.TaxRedeem(BigDecimal("12.00"), "SOME_TAX"),
            )
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("9500.00"),
                    startingYield = BigDecimal("100.00"),
                    yieldPercentage = BigDecimal("0.60"),
                    sellAmount = BigDecimal("500.00"),
                    taxes = taxes2,
                )
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("9000.00"),
            yield = BigDecimal("160.00"),
            statements = listOf(
                BondCalculationRecord.Yield(BigDecimal("60.00")),
                BondCalculationRecord.YieldRedeem(BigDecimal("100.00")),
                BondCalculationRecord.PrincipalRedeem(BigDecimal("400.00")),
                BondCalculationRecord.TaxRedeem(BigDecimal("23.00"), "SOME_TAX_2"),
            )
        )

        every { taxService.getTaxIncidencesBy(date1, consolidationContext.contributionDate) } returns taxes1
        every { taxService.getTaxIncidencesBy(date2, consolidationContext.contributionDate) } returns taxes2

        val result = service.calculateBondo(consolidationContext)

        result.principal shouldBe BigDecimal("9000.00")
        result.yieldAmount shouldBe BigDecimal("160.00")
        result.remainingSells shouldBe emptyMap()
        result.statements shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, date1, BigDecimal("100.00")),
            BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date1, BigDecimal("500.00"), 5),
            BondOrderStatementCreation.TaxIncidence(bondOrderId, date1, BigDecimal("12.00"), 5, "SOME_TAX"),
            BondOrderStatementCreation.Yield(bondOrderId, date2, BigDecimal("60.00")),
            BondOrderStatementCreation.YieldRedeem(bondOrderId, date2, BigDecimal("100.00"), 7),
            BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date2, BigDecimal("400.00"), 7),
            BondOrderStatementCreation.TaxIncidence(bondOrderId, date2, BigDecimal("23.00"), 7, "SOME_TAX_2"),
        )

        coVerify(exactly = 2) { calculator.calculate(any()) }
    }

    "should handle remaining redemption when sell amount is larger than available" {
        val bondOrderId = 1
        val date1 = LocalDate.parse("2024-01-16")

        val consolidationContext = bondConsolidationContext(
            bondOrderId = bondOrderId,
            dateRange = listOf(date1),
            principal = BigDecimal("1000.00"),
            yieldAmount = BigDecimal("0.00"),
            yieldPercentages = mapOf(
                date1 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.50"))
            ),
            sellOrders = mapOf(
                date1 to BondConsolidationContext.SellOrderContext(5, BigDecimal("1500.00"))
            )
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("1000.00"),
                    startingYield = BigDecimal("0.00"),
                    yieldPercentage = BigDecimal("0.50"),
                    sellAmount = BigDecimal("1500.00")
                )
            )
        } returns BondCalculationResult.RemainingRedemption(
            principal = BigDecimal("0.00"),
            yield = BigDecimal("5.00"),
            statements = listOf(
                BondCalculationRecord.Yield(BigDecimal("5.00")),
                BondCalculationRecord.PrincipalRedeem(BigDecimal("1000.00"))
            ),
            remainingRedemptionAmount = BigDecimal("500.00")
        )

        val result = service.calculateBondo(consolidationContext)

        result.remainingSells shouldBe mapOf(
            date1 to BondConsolidationContext.SellOrderContext(5, BigDecimal("500.00"))
        )
        result.statements shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, date1, BigDecimal("5.00")),
            BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date1, BigDecimal("1000.00"), 5)
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
            dateRange = listOf(date1, date2, date3),
            principal = BigDecimal("10000.00"),
            yieldAmount = BigDecimal("0.00"),
            yieldPercentages = mapOf(
                date1 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.50")),
                date2 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.40")),
                date3 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.60"))
            )
        )

        // Mock responses for each calculation in chronological order
        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("10000.00"),
                    startingYield = BigDecimal("0.00"),
                    yieldPercentage = BigDecimal("0.40"), // date2 comes first
                    sellAmount = BigDecimal("0.00")
                )
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("10000.00"),
            yield = BigDecimal("40.00"),
            statements = listOf(BondCalculationRecord.Yield(BigDecimal("40.00")))
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("10000.00"),
                    startingYield = BigDecimal("40.00"),
                    yieldPercentage = BigDecimal("0.50"), // date1 comes second
                    sellAmount = BigDecimal("0.00")
                )
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("10000.00"),
            yield = BigDecimal("90.00"),
            statements = listOf(BondCalculationRecord.Yield(BigDecimal("50.00")))
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("10000.00"),
                    startingYield = BigDecimal("90.00"),
                    yieldPercentage = BigDecimal("0.60"), // date3 comes third
                    sellAmount = BigDecimal("0.00")
                )
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("10000.00"),
            yield = BigDecimal("150.00"),
            statements = listOf(BondCalculationRecord.Yield(BigDecimal("60.00")))
        )

        val result = service.calculateBondo(consolidationContext)

        result.principal shouldBe BigDecimal("10000.00")
        result.yieldAmount shouldBe BigDecimal("150.00")
        result.statements shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, date2, BigDecimal("40.00")),
            BondOrderStatementCreation.Yield(bondOrderId, date1, BigDecimal("50.00")),
            BondOrderStatementCreation.Yield(bondOrderId, date3, BigDecimal("60.00"))
        )

        coVerify(exactly = 3) { calculator.calculate(any()) }
    }

    "should handle empty date range" {
        val consolidationContext = bondConsolidationContext(
            bondOrderId = 1,
            dateRange = listOf(),
            principal = BigDecimal("10000.00"),
            yieldAmount = BigDecimal("0.00"),
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
            dateRange = listOf(date1, date2, date3),
            principal = BigDecimal("1000.00"),
            yieldAmount = BigDecimal("500.00"),
            yieldPercentages = mapOf(
                date1 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.50")),
                date2 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.60")),
                date3 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.70")) // Should not be processed
            ),
            sellOrders = mapOf(
                date1 to BondConsolidationContext.SellOrderContext(5, BigDecimal("800.00")),
                date2 to BondConsolidationContext.SellOrderContext(6, BigDecimal("900.00"))
            )
        )

        // First calculation: reduces principal and yield but doesn't reach zero
        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("1000.00"),
                    startingYield = BigDecimal("500.00"),
                    yieldPercentage = BigDecimal("0.50"),
                    sellAmount = BigDecimal("800.00")
                )
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("200.00"),
            yield = BigDecimal("300.00"),
            statements = listOf(
                BondCalculationRecord.Yield(BigDecimal("50.00")),
                BondCalculationRecord.YieldRedeem(BigDecimal("250.00")),
                BondCalculationRecord.PrincipalRedeem(BigDecimal("500.00"))
            )
        )

        // Second calculation: reduces both principal and yield to zero
        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("200.00"),
                    startingYield = BigDecimal("300.00"),
                    yieldPercentage = BigDecimal("0.60"),
                    sellAmount = BigDecimal("900.00")
                )
            )
        } returns BondCalculationResult.RemainingRedemption(
            principal = BigDecimal("0.00"),
            yield = BigDecimal("0.00"), // Both reach zero - should stop processing here
            statements = listOf(
                BondCalculationRecord.Yield(BigDecimal("20.00")),
                BondCalculationRecord.YieldRedeem(BigDecimal("320.00")),
                BondCalculationRecord.PrincipalRedeem(BigDecimal("200.00"))
            ),
            remainingRedemptionAmount = BigDecimal("200.00"),
        )

        val result = service.calculateBondo(consolidationContext)

        result.principal shouldBe BigDecimal("0.00")
        result.yieldAmount shouldBe BigDecimal("0.00")
        result.statements shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, date1, BigDecimal("50.00")),
            BondOrderStatementCreation.YieldRedeem(bondOrderId, date1, BigDecimal("250.00"), 5),
            BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date1, BigDecimal("500.00"), 5),
            BondOrderStatementCreation.Yield(bondOrderId, date2, BigDecimal("20.00")),
            BondOrderStatementCreation.YieldRedeem(bondOrderId, date2, BigDecimal("320.00"), 6),
            BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date2, BigDecimal("200.00"), 6)
        )

        // The remaining sell orders should include the unprocessed date2 sell order
        result.remainingSells shouldBe mapOf(
            date2 to BondConsolidationContext.SellOrderContext(6, BigDecimal("200.00"))
        )

        // Should only call calculator twice (date1 and date2), not for date3
        coVerify(exactly = 2) { calculator.calculate(any()) }
    }

    "should process bond consolidation without yield percentage correctly and default to 0.00" {
        val bondOrderId = 1
        val date1 = LocalDate.parse("2024-01-16")

        val consolidationContext = bondConsolidationContext(
            bondOrderId = bondOrderId,
            dateRange = listOf(date1),
            principal = BigDecimal("10000.00"),
            yieldAmount = BigDecimal("0.00"),
            yieldPercentages = emptyMap(),
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("10000.00"),
                    startingYield = BigDecimal("0.00"),
                    yieldPercentage = BigDecimal("0.00"),
                )
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("10000.00"),
            yield = BigDecimal("0.00"),
            statements = listOf()
        )

        every { taxService.getTaxIncidencesBy(date1, consolidationContext.contributionDate) } returns emptySet()

        val result = service.calculateBondo(consolidationContext)

        result.principal shouldBe BigDecimal("10000.00")
        result.yieldAmount shouldBe BigDecimal("0.00")
        result.remainingSells shouldBe emptyMap()
        result.statements shouldBe emptyList()

        coVerify(exactly = 1) { calculator.calculate(any()) }
    }

    "should pass fullRedemption flag to calculator when full redemption date matches" {
        val bondOrderId = 1
        val date1 = LocalDate.parse("2024-01-16")
        val date2 = LocalDate.parse("2024-01-17")
        val fullRedemptionDate = LocalDate.parse("2024-01-17")

        val consolidationContext = bondConsolidationContext(
            bondOrderId = bondOrderId,
            dateRange = listOf(date1, date2),
            principal = BigDecimal("10000.00"),
            yieldAmount = BigDecimal("0.00"),
            yieldPercentages = mapOf(
                date1 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.50")),
                date2 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.60"))
            ),
            fullRedemption = FullRedemptionContext(
                id = 10,
                date = fullRedemptionDate
            )
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("10000.00"),
                    startingYield = BigDecimal("0.00"),
                    yieldPercentage = BigDecimal("0.50"),
                    sellAmount = BigDecimal("0.00"),
                ),
                fullRedemption = false, // date1 is not the full redemption date
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("10000.00"),
            yield = BigDecimal("50.00"),
            statements = listOf(
                BondCalculationRecord.Yield(BigDecimal("50.00")),
            )
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("10000.00"),
                    startingYield = BigDecimal("50.00"),
                    yieldPercentage = BigDecimal("0.60"),
                    sellAmount = BigDecimal("0.00"),
                ),
                fullRedemption = true, // date2 matches full redemption date
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("0.00"),
            yield = BigDecimal("0.00"),
            statements = listOf(
                BondCalculationRecord.Yield(BigDecimal("60.00")),
                BondCalculationRecord.YieldRedeem(BigDecimal("88.00")),
                BondCalculationRecord.PrincipalRedeem(BigDecimal("10000.00")),
            )
        )

        val result = service.calculateBondo(consolidationContext)

        result.principal shouldBe BigDecimal("0.00")
        result.yieldAmount shouldBe BigDecimal("0.00")
        result.remainingSells shouldBe emptyMap()
        result.statements shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, date1, BigDecimal("50.00")),
            BondOrderStatementCreation.Yield(bondOrderId, date2, BigDecimal("60.00")),
            BondOrderStatementCreation.YieldRedeem(bondOrderId, date2, BigDecimal("88.00"), 10),
            BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date2, BigDecimal("10000.00"), 10),
        )

        coVerify(exactly = 1) { calculator.calculate(any(), fullRedemption = false) }
        coVerify(exactly = 1) { calculator.calculate(any(), fullRedemption = true) }
    }

    "should not pass fullRedemption flag when there is no full redemption context" {
        val bondOrderId = 1
        val date1 = LocalDate.parse("2024-01-16")

        val consolidationContext = bondConsolidationContext(
            bondOrderId = bondOrderId,
            dateRange = listOf(date1),
            principal = BigDecimal("10000.00"),
            yieldAmount = BigDecimal("0.00"),
            yieldPercentages = mapOf(
                date1 to BondConsolidationContext.YieldPercentageContext(BigDecimal("0.50"))
            ),
            fullRedemption = null // No full redemption
        )

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("10000.00"),
                    startingYield = BigDecimal("0.00"),
                    yieldPercentage = BigDecimal("0.50"),
                    sellAmount = BigDecimal("0.00"),
                ),
                fullRedemption = false,
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("10000.00"),
            yield = BigDecimal("50.00"),
            statements = listOf(
                BondCalculationRecord.Yield(BigDecimal("50.00")),
            )
        )

        val result = service.calculateBondo(consolidationContext)

        result.principal shouldBe BigDecimal("10000.00")
        result.yieldAmount shouldBe BigDecimal("50.00")
        result.remainingSells shouldBe emptyMap()
        result.statements shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, date1, BigDecimal("50.00")),
        )

        coVerify(exactly = 1) { calculator.calculate(any(), fullRedemption = false) }
        coVerify(exactly = 0) { calculator.calculate(any(), fullRedemption = true) }
    }

    "should consolidate maturity and create proper statements" {
        val bondOrderId = 1
        val maturityOrderId = 10
        val maturityDate = LocalDate.parse("2024-06-30")
        val contributionDate = LocalDate.parse("2024-01-01")

        val maturityContext = bondMaturityConsolidationContext(
            bondOrderId = bondOrderId,
            maturityOrderId = maturityOrderId,
            date = maturityDate,
            contributionDate = contributionDate,
            principal = BigDecimal("10000.00"),
            yieldAmount = BigDecimal("500.00"),
        )

        val taxes = setOf(iofIncidence(), rendaIncidence())

        coEvery { taxService.getTaxIncidencesBy(maturityDate, contributionDate) } returns taxes

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("10000.00"),
                    startingYield = BigDecimal("500.00"),
                    taxes = taxes,
                ),
                fullRedemption = true,
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("0.00"),
            yield = BigDecimal("0.00"),
            statements = listOf(
                BondCalculationRecord.Yield(BigDecimal("50.00")),
                BondCalculationRecord.YieldRedeem(BigDecimal("550.00")),
                BondCalculationRecord.PrincipalRedeem(BigDecimal("10000.00")),
                BondCalculationRecord.TaxRedeem(BigDecimal("82.50"), "RENDA"),
            )
        )

        val result = service.consolidateMaturity(maturityContext)

        result shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, maturityDate, BigDecimal("50.00")),
            BondOrderStatementCreation.YieldRedeem(bondOrderId, maturityDate, BigDecimal("550.00"), maturityOrderId),
            BondOrderStatementCreation.PrincipalRedeem(
                bondOrderId,
                maturityDate,
                BigDecimal("10000.00"),
                maturityOrderId,
            ),
            BondOrderStatementCreation.TaxIncidence(
                bondOrderId,
                maturityDate,
                BigDecimal("82.50"),
                maturityOrderId,
                "RENDA",
            ),
        )

        coVerify(exactly = 1) { taxService.getTaxIncidencesBy(maturityDate, contributionDate) }
        coVerify(exactly = 1) { calculator.calculate(any(), fullRedemption = true) }
    }

    "should consolidate maturity with only principal (no yield)" {
        val bondOrderId = 2
        val maturityOrderId = 20
        val maturityDate = LocalDate.parse("2024-12-31")
        val contributionDate = LocalDate.parse("2024-01-15")

        val maturityContext = bondMaturityConsolidationContext(
            bondOrderId = bondOrderId,
            maturityOrderId = maturityOrderId,
            date = maturityDate,
            contributionDate = contributionDate,
            principal = BigDecimal("5000.00"),
            yieldAmount = BigDecimal("0.00"),
        )

        val taxes = setOf(rendaIncidence())

        coEvery {
            taxService.getTaxIncidencesBy(maturityDate, contributionDate)
        } returns taxes

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("5000.00"),
                    startingYield = BigDecimal("0.00"),
                    taxes = taxes,
                ),
                fullRedemption = true,
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("0.00"),
            yield = BigDecimal("0.00"),
            statements = listOf(
                BondCalculationRecord.PrincipalRedeem(BigDecimal("5000.00")),
            )
        )

        val result = service.consolidateMaturity(maturityContext)

        result shouldBe listOf(
            BondOrderStatementCreation.PrincipalRedeem(
                bondOrderId,
                maturityDate,
                BigDecimal("5000.00"),
                maturityOrderId,
            ),
        )

        coVerify(exactly = 1) { calculator.calculate(any(), fullRedemption = true) }
    }

    "should consolidate maturity with yield only (no principal)" {
        val bondOrderId = 3
        val maturityOrderId = 30
        val maturityDate = LocalDate.parse("2024-09-15")
        val contributionDate = LocalDate.parse("2024-06-01")

        val maturityContext = bondMaturityConsolidationContext(
            bondOrderId = bondOrderId,
            maturityOrderId = maturityOrderId,
            date = maturityDate,
            contributionDate = contributionDate,
            principal = BigDecimal("0.00"),
            yieldAmount = BigDecimal("1250.75"),
        )

        val taxes = setOf(iofIncidence(), rendaIncidence())

        coEvery {
            taxService.getTaxIncidencesBy(maturityDate, contributionDate)
        } returns taxes

        coEvery {
            calculator.calculate(
                bondCalculationContext(
                    principal = BigDecimal("0.00"),
                    startingYield = BigDecimal("1250.75"),
                    taxes = taxes,
                ),
                fullRedemption = true,
            )
        } returns BondCalculationResult.Ok(
            principal = BigDecimal("0.00"),
            yield = BigDecimal("0.00"),
            statements = listOf(
                BondCalculationRecord.Yield(BigDecimal("25.50")),
                BondCalculationRecord.YieldRedeem(BigDecimal("1276.25")),
                BondCalculationRecord.TaxRedeem(BigDecimal("191.44"), "RENDA"),
            )
        )

        val result = service.consolidateMaturity(maturityContext)

        result shouldBe listOf(
            BondOrderStatementCreation.Yield(bondOrderId, maturityDate, BigDecimal("25.50")),
            BondOrderStatementCreation.YieldRedeem(bondOrderId, maturityDate, BigDecimal("1276.25"), maturityOrderId),
            BondOrderStatementCreation.TaxIncidence(
                bondOrderId,
                maturityDate,
                BigDecimal("191.44"),
                maturityOrderId,
                "RENDA",
            ),
        )

        coVerify(exactly = 1) { calculator.calculate(any(), fullRedemption = true) }
    }
})
