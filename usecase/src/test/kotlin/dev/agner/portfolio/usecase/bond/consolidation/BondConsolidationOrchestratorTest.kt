package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationResult
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

class BondConsolidationOrchestratorTest : StringSpec({

    val repository = mockk<IBondOrderStatementRepository>()
    val bondOrderService = mockk<BondOrderService>()
    val indexValueService = mockk<IndexValueService>()
    val consolidator = mockk<BondConsolidator>()

    val orchestrator = BondConsolidationOrchestrator(repository, bondOrderService, indexValueService, consolidator)

    beforeEach {
        clearAllMocks()
    }

    "should consolidate floating rate bond orders with buy and sell orders" {
        val bondId = 1
        val indexId = IndexId.CDI
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate = LocalDate.parse("2024-01-16")
        val lastStatementDate = LocalDate.parse("2024-01-15")

        val floatingRateBond = FloatingRateBond(
            id = bondId,
            name = "Test Bond",
            value = 5.0,
            indexId = indexId
        )

        val buyOrder = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = 10000.0
        )

        val sellOrder = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate,
            amount = 1000.0
        )

        val lastStatement = BondOrderStatement(
            id = 1,
            buyOrderId = 1,
            date = lastStatementDate,
            amount = 0.0
        )

        val indexValues = listOf(
            IndexValue(date = sellDate, value = 100.0)
        )

        val consolidationResult = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(1, sellDate, 50.0),
                BondOrderStatementCreation.PrincipalRedeem(1, sellDate, 1000.0, 2)
            )
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder, sellOrder)
        coEvery { repository.fetchLastByBondOrderId(1) } returns lastStatement
        coEvery { indexValueService.fetchAllBy(indexId, lastStatementDate.nextDay()) } returns indexValues
        coEvery { repository.sumUpConsolidatedValues(1, lastStatementDate.nextDay()) } returns (500.0 to 25.0)
        coEvery { consolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        orchestrator.consolidateBy(bondId)

        coVerify(exactly = 1) { bondOrderService.fetchByBondId(bondId) }
        coVerify(exactly = 1) { repository.fetchLastByBondOrderId(1) }
        coVerify(exactly = 1) { indexValueService.fetchAllBy(indexId, lastStatementDate.nextDay()) }
        coVerify(exactly = 1) { repository.sumUpConsolidatedValues(1, lastStatementDate.nextDay()) }
        coVerify(exactly = 1) {
            consolidator.calculateBondo(
                BondConsolidationContext(
                    bondOrderId = 1,
                    principal = 9500.0,
                    yieldAmount = 25.0,
                    yieldPercentages = mapOf(
                        sellDate to BondConsolidationContext.YieldPercentageContext(5.0, indexValues[0])
                    ),
                    sellOrders = mapOf(
                        sellDate to BondConsolidationContext.SellOrderContext(2, 1000.0)
                    )
                )
            )
        }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
    }

    "should use order date when no previous statement exists" {
        val bondId = 1
        val indexId = IndexId.CDI
        val orderDate = LocalDate.parse("2024-01-10")

        val floatingRateBond = FloatingRateBond(
            id = bondId,
            name = "Test Bond",
            value = 3.0,
            indexId = indexId
        )

        val buyOrder = BondOrder(
            id = 100,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = 5000.0
        )

        val consolidationResult = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = emptyList()
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder)
        coEvery { repository.fetchLastByBondOrderId(100) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(100, orderDate) } returns (0.0 to 0.0)
        coEvery { consolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        orchestrator.consolidateBy(bondId)

        coVerify { repository.fetchLastByBondOrderId(100) }
        coVerify { indexValueService.fetchAllBy(indexId, orderDate) }
        coVerify { repository.sumUpConsolidatedValues(100, orderDate) }
        coVerify {
            consolidator.calculateBondo(
                BondConsolidationContext(
                    bondOrderId = 100,
                    principal = 5000.0,
                    yieldAmount = 0.0,
                    yieldPercentages = emptyMap(),
                    sellOrders = emptyMap()
                )
            )
        }
    }

    "should process multiple buy orders in chronological order" {
        val bondId = 1
        val indexId = IndexId.CDI
        val date1 = LocalDate.parse("2024-01-10")
        val date2 = LocalDate.parse("2024-01-20")
        val sellDate = LocalDate.parse("2024-01-25")

        val floatingRateBond = FloatingRateBond(
            id = bondId,
            name = "Test Bond",
            value = 4.0,
            indexId = indexId
        )

        val buyOrder1 = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = date2, // Later date but added first
            amount = 8000.0
        )

        val buyOrder2 = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = date1, // Earlier date
            amount = 5000.0
        )

        val sellOrder = BondOrder(
            id = 3,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate,
            amount = 2000.0
        )

        val indexValues = listOf(
            IndexValue(date = sellDate, value = 102.0)
        )

        val consolidationResult1 = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(2, sellDate, 20.0)
            )
        )

        val consolidationResult2 = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(1, sellDate, 30.0),
                BondOrderStatementCreation.PrincipalRedeem(1, sellDate, 2000.0, 3)
            )
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder1, buyOrder2, sellOrder)
        coEvery { repository.fetchLastByBondOrderId(2) } returns null // First order chronologically
        coEvery { repository.fetchLastByBondOrderId(1) } returns null // Second order chronologically
        coEvery { indexValueService.fetchAllBy(indexId, date1) } returns indexValues
        coEvery { indexValueService.fetchAllBy(indexId, date2) } returns indexValues
        coEvery { repository.sumUpConsolidatedValues(2, date1) } returns (0.0 to 0.0)
        coEvery { repository.sumUpConsolidatedValues(1, date2) } returns (0.0 to 0.0)
        coEvery { consolidator.calculateBondo(any()) } returnsMany listOf(consolidationResult1, consolidationResult2)
        coEvery { repository.saveAll(any()) } just Runs

        orchestrator.consolidateBy(bondId)

        coVerify(exactly = 1) {
            consolidator.calculateBondo(
                BondConsolidationContext(
                    bondOrderId = 2,
                    principal = 5000.0,
                    yieldAmount = 0.0,
                    yieldPercentages = indexValues.associate {
                        it.date to BondConsolidationContext.YieldPercentageContext(floatingRateBond.value, it)
                    },
                    sellOrders = mapOf(
                        sellDate to BondConsolidationContext.SellOrderContext(3, 2000.0),
                    ),
                )
            )
        }
        coVerify(exactly = 1) {
            consolidator.calculateBondo(
                BondConsolidationContext(
                    bondOrderId = 1,
                    principal = 8000.0,
                    yieldAmount = 0.0,
                    yieldPercentages = indexValues.associate {
                        it.date to BondConsolidationContext.YieldPercentageContext(floatingRateBond.value, it)
                    },
                    sellOrders = emptyMap(),
                )
            )
        }
        coVerify(exactly = 1) {
            repository.saveAll(consolidationResult1.statements + consolidationResult2.statements)
        }
    }

    "should filter out buy orders and only process sell orders for mapping" {
        val bondId = 1
        val indexId = IndexId.CDI
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate1 = LocalDate.parse("2024-01-15")
        val sellDate2 = LocalDate.parse("2024-01-20")

        val floatingRateBond = FloatingRateBond(
            id = bondId,
            name = "Test Bond",
            value = 3.5,
            indexId = indexId
        )

        val buyOrder = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = 10000.0
        )

        val sellOrder1 = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate1,
            amount = 1500.0
        )

        val sellOrder2 = BondOrder(
            id = 3,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate2,
            amount = 2500.0
        )

        val consolidationResult = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = emptyList()
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder, sellOrder1, sellOrder2)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (0.0 to 0.0)
        coEvery { consolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        orchestrator.consolidateBy(bondId)

        coVerify {
            consolidator.calculateBondo(
                BondConsolidationContext(
                    bondOrderId = 1,
                    principal = 10000.0,
                    yieldAmount = 0.0,
                    yieldPercentages = emptyMap(),
                    sellOrders = mapOf(
                        sellDate1 to BondConsolidationContext.SellOrderContext(2, 1500.0),
                        sellDate2 to BondConsolidationContext.SellOrderContext(3, 2500.0)
                    )
                )
            )
        }
    }

    "should filter out already redeemed buy orders from consolidation" {
        val bondId = 1
        val indexId = IndexId.CDI
        val orderDate = LocalDate.parse("2024-01-01")

        val floatingRateBond = FloatingRateBond(
            id = bondId,
            name = "Test Bond",
            value = 4.0,
            indexId = indexId
        )

        val buyOrder1 = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = 5000.0
        )

        val buyOrder2 = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = 8000.0
        )

        val alreadyRedeemedBuyIds = setOf(1) // Order 1 is already fully redeemed

        val consolidationResult = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(2, orderDate, 30.0)
            )
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns alreadyRedeemedBuyIds
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder1, buyOrder2)
        coEvery { repository.fetchLastByBondOrderId(2) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(2, orderDate) } returns (0.0 to 0.0)
        coEvery { consolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        orchestrator.consolidateBy(bondId)

        // Verify that only the non-redeemed buy order (id=2) is processed
        coVerify(exactly = 1) { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) }
        coVerify(exactly = 1) { repository.fetchLastByBondOrderId(2) }
        coVerify(exactly = 0) { repository.fetchLastByBondOrderId(1) } // Should not be called for redeemed order
        coVerify(exactly = 1) {
            consolidator.calculateBondo(
                BondConsolidationContext(
                    bondOrderId = 2,
                    principal = 8000.0,
                    yieldAmount = 0.0,
                    yieldPercentages = emptyMap(),
                    sellOrders = emptyMap()
                )
            )
        }
    }

    "should filter out already consolidated sell orders from sell order mapping" {
        val bondId = 1
        val indexId = IndexId.CDI
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate1 = LocalDate.parse("2024-01-15")
        val sellDate2 = LocalDate.parse("2024-01-20")

        val floatingRateBond = FloatingRateBond(
            id = bondId,
            name = "Test Bond",
            value = 3.5,
            indexId = indexId
        )

        val buyOrder = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = 10000.0
        )

        val sellOrder1 = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate1,
            amount = 1500.0
        )

        val sellOrder2 = BondOrder(
            id = 3,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate2,
            amount = 2500.0
        )

        val alreadyRedeemedBuyIds = emptySet<Int>()
        val consolidatedSellIds = setOf(2) // Only sell order 2 is consolidated

        val consolidationResult = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(1, sellDate2, 25.0),
                BondOrderStatementCreation.PrincipalRedeem(1, sellDate2, 2500.0, 3)
            )
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns alreadyRedeemedBuyIds
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns consolidatedSellIds
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder, sellOrder1, sellOrder2)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (0.0 to 0.0)
        coEvery { consolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        orchestrator.consolidateBy(bondId)

        // Verify that only the non-consolidated sell order (id=3) is included in sell orders mapping
        coVerify(exactly = 1) { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) }
        coVerify {
            consolidator.calculateBondo(
                BondConsolidationContext(
                    bondOrderId = 1,
                    principal = 10000.0,
                    yieldAmount = 0.0,
                    yieldPercentages = emptyMap(),
                    sellOrders = mapOf(
                        // Only sell order 3 should be present, sell order 2 is filtered out
                        sellDate2 to BondConsolidationContext.SellOrderContext(3, 2500.0)
                    )
                )
            )
        }
    }

    "should handle scenario with both redeemed buys and consolidated sells filtered out" {
        val bondId = 1
        val indexId = IndexId.CDI
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate = LocalDate.parse("2024-01-15")

        val floatingRateBond = FloatingRateBond(
            id = bondId,
            name = "Test Bond",
            value = 4.5,
            indexId = indexId
        )

        val buyOrder1 = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = 5000.0
        )

        val buyOrder2 = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = 7000.0
        )

        val sellOrder1 = BondOrder(
            id = 3,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate,
            amount = 1000.0
        )

        val sellOrder2 = BondOrder(
            id = 4,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate,
            amount = 2000.0
        )

        val alreadyRedeemedBuyIds = setOf(1) // Buy order 1 is already redeemed
        val consolidatedSellIds = setOf(3) // Only sell order 3 is consolidated

        val consolidationResult = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(2, sellDate, 35.0),
                BondOrderStatementCreation.PrincipalRedeem(2, sellDate, 2000.0, 4)
            )
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns alreadyRedeemedBuyIds
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns consolidatedSellIds
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder1, buyOrder2, sellOrder1, sellOrder2)
        coEvery { repository.fetchLastByBondOrderId(2) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(2, orderDate) } returns (0.0 to 0.0)
        coEvery { consolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        orchestrator.consolidateBy(bondId)

        // Verify both filtering methods are called
        coVerify(exactly = 1) { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) }
        coVerify(exactly = 1) { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) }

        // Verify only non-redeemed buy order is processed
        coVerify(exactly = 1) { repository.fetchLastByBondOrderId(2) }
        coVerify(exactly = 0) { repository.fetchLastByBondOrderId(1) }

        // Verify only non-consolidated sell order is included
        coVerify {
            consolidator.calculateBondo(
                BondConsolidationContext(
                    bondOrderId = 2,
                    principal = 7000.0,
                    yieldAmount = 0.0,
                    yieldPercentages = emptyMap(),
                    sellOrders = mapOf(
                        sellDate to BondConsolidationContext.SellOrderContext(4, 2000.0)
                    )
                )
            )
        }
    }
})
