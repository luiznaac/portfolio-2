package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.model.FloatingRateBond
import dev.agner.portfolio.usecase.bond.repository.IBondOrderYieldRepository
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class BondOrderStatementServiceTest : StringSpec({

    val repository = mockk<IBondOrderYieldRepository>()
    val bondOrderService = mockk<BondOrderService>()
    val indexValueService = mockk<IndexValueService>()

    val service = BondOrderStatementService(repository, bondOrderService, indexValueService)

    beforeEach {
        clearAllMocks()
    }

    "should process floating rate bond orders correctly" {
        val bondId = 1
        val indexId = IndexId.CDI
        val orderDate = LocalDate(2024, 1, 1)
        val calculationStartDate = LocalDate(2024, 1, 15)
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
        val indexValues = listOf(
            IndexValue(date = LocalDate(2024, 1, 16), value = 100.0),
            IndexValue(date = LocalDate(2024, 1, 17), value = 101.0),
            IndexValue(date = LocalDate(2024, 1, 18), value = 102.0)
        )

        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(bondOrder)
        coEvery { repository.fetchLastByBondOrderId(100) } returns BondOrderStatement(
            id = 1,
            bondOrderId = 1,
            date = calculationStartDate,
            amount = 0.0,
        )
        coEvery { indexValueService.fetchAllBy(indexId, calculationStartDate) } returns indexValues
        coEvery { repository.saveAll(any()) } just Runs

        service.consolidateBy(bondId)

        coVerify { bondOrderService.fetchByBondId(bondId) }
        coVerify { repository.fetchLastByBondOrderId(100) }
        coVerify { indexValueService.fetchAllBy(indexId, calculationStartDate) }
        coVerify { repository.saveAll(any()) }
    }

    "should use order date when no previous yield exists" {
        val bondId = 1
        val indexId = IndexId.CDI
        val orderDate = LocalDate(2024, 1, 1)
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
        val indexValues = listOf(
            IndexValue(date = LocalDate(2024, 1, 2), value = 100.0)
        )

        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(bondOrder)
        coEvery { repository.fetchLastByBondOrderId(100) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns indexValues
        coEvery { repository.saveAll(any()) } just Runs

        service.consolidateBy(bondId)

        coVerify { indexValueService.fetchAllBy(indexId, orderDate) }
    }

    "should handle multiple orders with different calculation start dates" {
        val bondId = 1
        val indexId = IndexId.CDI
        val order1Date = LocalDate(2024, 1, 1)
        val order2Date = LocalDate(2024, 1, 5)
        val earlierYieldDate = LocalDate(2023, 12, 15)
        val laterYieldDate = LocalDate(2024, 1, 3)
        val floatingRateBond = FloatingRateBond(
            id = bondId,
            name = "Test Bond",
            value = 5.0,
            indexId = indexId
        )
        val orders = listOf(
            BondOrder(id = 100, bond = floatingRateBond, type = BondOrderType.BUY, date = order1Date, amount = 10000.0),
            BondOrder(id = 101, bond = floatingRateBond, type = BondOrderType.BUY, date = order2Date, amount = 5000.0)
        )
        val indexValues = listOf(
            IndexValue(date = LocalDate(2024, 1, 2), value = 100.0)
        )

        coEvery { bondOrderService.fetchByBondId(bondId) } returns orders
        coEvery { repository.fetchLastByBondOrderId(100) } returns BondOrderStatement(1, 1, earlierYieldDate, 0.0)
        coEvery { repository.fetchLastByBondOrderId(101) } returns BondOrderStatement(2, 2, laterYieldDate, 0.0)
        coEvery { indexValueService.fetchAllBy(indexId, earlierYieldDate) } returns indexValues
        coEvery { repository.saveAll(any()) } just Runs

        service.consolidateBy(bondId)

        coVerify { indexValueService.fetchAllBy(indexId, earlierYieldDate) }
    }

    "should batch save yield creations in chunks of 100" {
        val bondId = 1
        val indexId = IndexId.CDI
        val orderDate = LocalDate(2024, 1, 1)
        val floatingRateBond = FloatingRateBond(
            id = bondId,
            name = "Test Bond",
            value = 1.0,
            indexId = indexId
        )
        val bondOrder = BondOrder(
            id = 100,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = 10000.0
        )
        val indexValues = (1..250).map { day ->
            IndexValue(date = LocalDate(2024, 1, 1).plus(day, DateTimeUnit.DAY), value = 100.0)
        }

        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(bondOrder)
        coEvery { repository.fetchLastByBondOrderId(100) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns indexValues
        coEvery { repository.saveAll(any()) } just Runs

        service.consolidateBy(bondId)

        coVerify(exactly = 3) { repository.saveAll(any()) }
    }
})
