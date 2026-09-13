package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.BondMaturityConsolidationContextFixture
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation.PrincipalRedeemCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation.YieldCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bondConsolidationResult
import dev.agner.portfolio.usecase.floatingRateBond
import io.kotest.core.spec.style.StringSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

class BondConsolidationServiceMaturityTest : StringSpec({

    val env = bondConsolidationServiceTestSupport()
    val repository = env.repository
    val bondOrderService = env.bondOrderService
    val indexValueService = env.indexValueService
    val contributionConsolidator = env.contributionConsolidator
    val positionService = env.positionService
    val clock = env.clock
    val consolidator = env.consolidator

    beforeEach {
        clearAllMocks()
        every { clock.zone } returns ZoneId.systemDefault()
    }

    "should handle maturity when final date equals maturity date and has remaining balance" {
        val maturityDate = LocalDate.parse("2024-06-30")
        val floatingRateBond = floatingRateBond(maturityDate = maturityDate)
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")

        val buyOrder = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("10000.00"),
        )

        val maturityOrder = BondOrder.DownToZero.Maturity(
            id = 100,
            bond = floatingRateBond,
            date = maturityDate,
        )

        val consolidationResult = bondConsolidationResult(
            principal = BigDecimal("10000.00"),
            yieldAmount = BigDecimal("500.00"),
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(1, maturityDate, BigDecimal("50.00")),
            ),
        )

        val maturityStatements = listOf(
            BondOrderStatementCreation.YieldRedeemCreation(1, maturityDate, BigDecimal("550.00"), 100),
            PrincipalRedeemCreation(1, maturityDate, BigDecimal("10000.00"), 100),
        )

        every { clock.instant() } returns Instant.parse("2024-07-01T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { bondOrderService.create(any(), any()) } returns maturityOrder
        coEvery {
            contributionConsolidator.consolidateMaturity(
                BondMaturityConsolidationContextFixture(
                    bondOrderId = 1,
                    maturityOrderId = 100,
                    date = maturityDate,
                    contributionDate = orderDate,
                    principal = BigDecimal("10000.00"),
                    yieldAmount = BigDecimal("500.00"),
                ).toContext(),
            )
        } returns maturityStatements
        coEvery { repository.saveAll(any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 1) {
            bondOrderService.create(
                BondOrderCreation(
                    bondId = bondId,
                    type = BondOrderType.MATURITY,
                    date = maturityDate,
                    amount = BigDecimal("0.00"),
                ),
                isInternal = true,
            )
        }
        coVerify(exactly = 1) {
            contributionConsolidator.consolidateMaturity(
                BondMaturityConsolidationContextFixture(
                    bondOrderId = 1,
                    maturityOrderId = 100,
                    date = maturityDate,
                    contributionDate = orderDate,
                    principal = BigDecimal("10000.00"),
                    yieldAmount = BigDecimal("500.00"),
                ).toContext(),
            )
        }
        coVerify(exactly = 1) {
            repository.saveAll(consolidationResult.statements + maturityStatements)
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder)) }
    }

    "should not handle maturity when final date is before maturity date" {
        val maturityDate = LocalDate.parse("2024-12-31")
        val floatingRateBond = floatingRateBond(maturityDate = maturityDate)
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")
        val yesterdayDate = LocalDate.parse("2024-06-30") // Before maturity

        val buyOrder = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("10000.00"),
        )

        val consolidationResult = bondConsolidationResult(
            principal = BigDecimal("10000.00"),
            yieldAmount = BigDecimal("300.00"),
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(1, yesterdayDate, BigDecimal("30.00")),
            ),
        )

        every { clock.instant() } returns Instant.parse("2024-07-01T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 0) { bondOrderService.create(any(), any()) }
        coVerify(exactly = 0) { contributionConsolidator.consolidateMaturity(any()) }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder)) }
    }

    "should not handle maturity when principal and yield are zero" {
        val maturityDate = LocalDate.parse("2024-06-30")
        val floatingRateBond = floatingRateBond(maturityDate = maturityDate)
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")

        val buyOrder = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("10000.00"),
        )

        val consolidationResult = bondConsolidationResult(
            principal = BigDecimal("0.00"),
            yieldAmount = BigDecimal("0.00"),
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(1, maturityDate, BigDecimal("50.00")),
                BondOrderStatementCreation.YieldRedeemCreation(1, maturityDate, BigDecimal("550.00"), 2),
                PrincipalRedeemCreation(1, maturityDate, BigDecimal("10000.00"), 2),
            ),
        )

        every { clock.instant() } returns Instant.parse("2024-07-01T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 0) { bondOrderService.create(any(), any()) }
        coVerify(exactly = 0) { contributionConsolidator.consolidateMaturity(any()) }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder)) }
    }

    "should handle maturity for multiple buy orders reaching maturity" {
        val maturityDate = LocalDate.parse("2024-08-31")
        val floatingRateBond = floatingRateBond(maturityDate = maturityDate)
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate1 = LocalDate.parse("2024-01-01")
        val orderDate2 = LocalDate.parse("2024-02-01")

        val buyOrder1 = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate1,
            amount = BigDecimal("3000.00"),
        )

        val buyOrder2 = BondOrder.Contribution.Buy(
            id = 2,
            bond = floatingRateBond,
            date = orderDate2,
            amount = BigDecimal("7000.00"),
        )

        val maturityOrder1 = BondOrder.DownToZero.Maturity(
            id = 101,
            bond = floatingRateBond,
            date = maturityDate,
        )

        val maturityOrder2 = BondOrder.DownToZero.Maturity(
            id = 102,
            bond = floatingRateBond,
            date = maturityDate,
        )

        val consolidationResult1 = bondConsolidationResult(
            principal = BigDecimal("3000.00"),
            yieldAmount = BigDecimal("180.00"),
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(1, maturityDate, BigDecimal("18.00")),
            ),
        )

        val consolidationResult2 = bondConsolidationResult(
            principal = BigDecimal("7000.00"),
            yieldAmount = BigDecimal("350.00"),
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(2, maturityDate, BigDecimal("35.00")),
            ),
        )

        val maturityStatements1 = listOf(
            BondOrderStatementCreation.YieldRedeemCreation(1, maturityDate, BigDecimal("198.00"), 101),
            PrincipalRedeemCreation(1, maturityDate, BigDecimal("3000.00"), 101),
        )

        val maturityStatements2 = listOf(
            BondOrderStatementCreation.YieldRedeemCreation(2, maturityDate, BigDecimal("385.00"), 102),
            PrincipalRedeemCreation(2, maturityDate, BigDecimal("7000.00"), 102),
        )

        every { clock.instant() } returns Instant.parse("2024-09-01T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder1, buyOrder2)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { repository.fetchLastByBondOrderId(2) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate1) } returns emptyList()
        coEvery { indexValueService.fetchAllBy(indexId, orderDate2) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate1) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { repository.sumUpConsolidatedValues(2, orderDate2) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery {
            contributionConsolidator.calculateBondo(any())
        } returnsMany listOf(consolidationResult1, consolidationResult2)
        coEvery { bondOrderService.create(any(), any()) } returnsMany listOf(maturityOrder1, maturityOrder2)
        coEvery {
            contributionConsolidator.consolidateMaturity(any())
        } returnsMany listOf(maturityStatements1, maturityStatements2)
        coEvery { repository.saveAll(any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 2) { bondOrderService.create(any(), any()) }
        coVerify(exactly = 2) { contributionConsolidator.consolidateMaturity(any()) }
        coVerify(exactly = 1) {
            repository.saveAll(
                consolidationResult1.statements + maturityStatements1 +
                    consolidationResult2.statements + maturityStatements2,
            )
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder1, buyOrder2)) }
    }
})
