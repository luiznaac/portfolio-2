package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.model.FloatingRateBond
import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import dev.agner.portfolio.usecase.extension.nextDay
import dev.agner.portfolio.usecase.index.IndexValueService
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.IndexValue
import io.kotest.core.spec.style.StringSpec
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class BondConsolidatorTest : StringSpec({

    val repository = mockk<IBondOrderStatementRepository>()
    val bondOrderService = mockk<BondOrderService>()
    val indexValueService = mockk<IndexValueService>()
    val calculator = mockk<BondCalculator>()

    val service = BondConsolidator(repository, bondOrderService, indexValueService, calculator)

    beforeEach {
        clearAllMocks()
    }

    "should process floating rate bond orders correctly" {
        val bondId = 1
        val indexId = IndexId.CDI
        val orderDate = LocalDate.parse("2024-01-01")
        val calculationStartDate = LocalDate.parse("2024-01-15")
        val floatingRateBond = FloatingRateBond(
            id = bondId,
            name = "Test Bond",
            value = 5.0,
            indexId = indexId
        )
        val bondOrder = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = 10000.0
        )
        val sellOrder1 = BondOrder(
            id = 5,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = LocalDate.parse("2024-01-16"),
            amount = 500.0
        )
        val sellOrder2 = BondOrder(
            id = 7,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = LocalDate.parse("2024-01-17"),
            amount = 500.0
        )
        val indexValues = listOf(
            IndexValue(date = LocalDate.parse("2024-01-16"), value = 100.0),
            IndexValue(date = LocalDate.parse("2024-01-17"), value = 101.0),
        )

        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(bondOrder, sellOrder1, sellOrder2)
        coEvery { repository.fetchLastByBondOrderId(any()) } returns BondOrderStatement(
            id = 1,
            bondOrderId = 1,
            date = calculationStartDate,
            amount = 0.0,
        )
        coEvery { indexValueService.fetchAllBy(indexId, calculationStartDate.nextDay()) } returns indexValues
        coEvery { repository.sumUpConsolidatedValues(any(), any()) } returns (0.0 to 0.0)
        coEvery { calculator.calculate(any()) } returnsMany listOf(
            BondCalculationResult.Ok(
                principal = 10000.0,
                yield = 500.0,
                statements = listOf(
                    BondCalculationRecord.Yield(100.0),
                    BondCalculationRecord.PrincipalRedeem(33.0),
                )
            ),
            BondCalculationResult.Ok(
                principal = 10000.0,
                yield = 800.0,
                statements = listOf(
                    BondCalculationRecord.Yield(300.0),
                    BondCalculationRecord.YieldRedeem(22.0),
                )
            )
        )
        coEvery { repository.saveAll(any()) } just Runs

        service.consolidateBy(bondId)

        coVerify(exactly = 1) { bondOrderService.fetchByBondId(bondId) }
        coVerify(exactly = 1) { repository.fetchLastByBondOrderId(1) }
        coVerify(exactly = 1) { indexValueService.fetchAllBy(indexId, calculationStartDate.nextDay()) }
        coVerify(exactly = 1) {
            repository.saveAll(
                listOf(
                    BondOrderStatementCreation.Yield(
                        bondOrderId = 1,
                        date = LocalDate.parse("2024-01-16"),
                        amount = 100.0
                    ),
                    BondOrderStatementCreation.PrincipalRedeem(
                        bondOrderId = 1,
                        date = LocalDate.parse("2024-01-16"),
                        amount = 33.0,
                        sellBondOrderId = 0,
                    ),
                    BondOrderStatementCreation.Yield(
                        bondOrderId = 1,
                        date = LocalDate.parse("2024-01-17"),
                        amount = 300.0
                    ),
                    BondOrderStatementCreation.YieldRedeem(
                        bondOrderId = 1,
                        date = LocalDate.parse("2024-01-17"),
                        amount = 22.0,
                        sellBondOrderId = 0,
                    ),
                )
            )
        }
    }

    "should use order date when no previous yield exists" {
        val bondId = 1
        val indexId = IndexId.CDI
        val orderDate = LocalDate.parse("2024-01-10")
        val floatingRateBond = FloatingRateBond(
            id = bondId,
            name = "Test Bond",
            value = 5.0,
            indexId = indexId
        )
        val bondOrder = BondOrder(
            id = 100,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = 10000.0
        )

        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(bondOrder)
        coEvery { repository.fetchLastByBondOrderId(100) } returns null
        coEvery { repository.sumUpConsolidatedValues(100, orderDate) } returns (0.0 to 0.0)
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()

        service.consolidateBy(bondId)

        coVerify { indexValueService.fetchAllBy(indexId, orderDate) }
    }
})
