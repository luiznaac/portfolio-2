package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.SellOrderContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.YieldPercentageContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationResult
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import dev.agner.portfolio.usecase.bondConsolidationContext
import dev.agner.portfolio.usecase.commons.nextDay
import dev.agner.portfolio.usecase.floatingRateBond
import dev.agner.portfolio.usecase.index.IndexValueService
import dev.agner.portfolio.usecase.index.model.IndexValue
import io.kotest.core.spec.style.StringSpec
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

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
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate = LocalDate.parse("2024-01-16")
        val lastStatementDate = LocalDate.parse("2024-01-15")

        val buyOrder = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = BigDecimal("10000.00")
        )

        val sellOrder = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate,
            amount = BigDecimal("1000.00")
        )

        val lastStatement = BondOrderStatement(
            id = 1,
            buyOrderId = 1,
            date = lastStatementDate,
            type = "YIELD",
            amount = BigDecimal("0.00"),
        )

        val indexValues = listOf(
            IndexValue(date = sellDate, value = BigDecimal("100.00"))
        )

        val consolidationResult = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(1, sellDate, BigDecimal("50.00")),
                BondOrderStatementCreation.PrincipalRedeem(1, sellDate, BigDecimal("1000.00"), 2)
            )
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder, sellOrder)
        coEvery { repository.fetchLastByBondOrderId(1) } returns lastStatement
        coEvery { indexValueService.fetchAllBy(indexId, lastStatementDate.nextDay()) } returns indexValues
        coEvery {
            repository.sumUpConsolidatedValues(1, lastStatementDate.nextDay())
        } returns (BigDecimal("500.00") to BigDecimal("25.00"))
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
                    contributionDate = orderDate,
                    principal = BigDecimal("9500.00"),
                    yieldAmount = BigDecimal("25.00"),
                    yieldPercentages = mapOf(
                        sellDate to YieldPercentageContext(floatingRateBond.value, indexValues[0])
                    ),
                    sellOrders = mapOf(
                        sellDate to SellOrderContext(2, BigDecimal("1000.00"))
                    )
                )
            )
        }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
    }

    "should use order date when no previous statement exists" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-10")

        val buyOrder = BondOrder(
            id = 100,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = BigDecimal("5000.00")
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
        coEvery { repository.sumUpConsolidatedValues(100, orderDate) } returns
            (BigDecimal("0.00") to BigDecimal("0.00"))
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
                    contributionDate = orderDate,
                    principal = BigDecimal("5000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = emptyMap(),
                    sellOrders = emptyMap()
                )
            )
        }
    }

    "should process multiple buy orders in chronological order" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val date1 = LocalDate.parse("2024-01-10")
        val date2 = LocalDate.parse("2024-01-20")
        val sellDate = LocalDate.parse("2024-01-25")

        val buyOrder1 = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = date2, // Later date but added first
            amount = BigDecimal("8000.00")
        )

        val buyOrder2 = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = date1, // Earlier date
            amount = BigDecimal("5000.00")
        )

        val sellOrder = BondOrder(
            id = 3,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate,
            amount = BigDecimal("2000.00")
        )

        val indexValues = listOf(
            IndexValue(date = sellDate, value = BigDecimal("102.00"))
        )

        val consolidationResult1 = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(2, sellDate, BigDecimal("20.00"))
            )
        )

        val consolidationResult2 = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(1, sellDate, BigDecimal("30.00")),
                BondOrderStatementCreation.PrincipalRedeem(1, sellDate, BigDecimal("2000.00"), 3)
            )
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder1, buyOrder2, sellOrder)
        coEvery { repository.fetchLastByBondOrderId(2) } returns null // First order chronologically
        coEvery { repository.fetchLastByBondOrderId(1) } returns null // Second order chronologically
        coEvery { indexValueService.fetchAllBy(indexId, date1) } returns indexValues
        coEvery { indexValueService.fetchAllBy(indexId, date2) } returns indexValues
        coEvery { repository.sumUpConsolidatedValues(2, date1) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { repository.sumUpConsolidatedValues(1, date2) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { consolidator.calculateBondo(any()) } returnsMany listOf(consolidationResult1, consolidationResult2)
        coEvery { repository.saveAll(any()) } just Runs

        orchestrator.consolidateBy(bondId)

        coVerify(exactly = 1) {
            consolidator.calculateBondo(
                bondConsolidationContext(
                    bondOrderId = 2,
                    contributionDate = date1,
                    principal = BigDecimal("5000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = indexValues.associate {
                        it.date to YieldPercentageContext(floatingRateBond.value, it)
                    },
                    sellOrders = mapOf(
                        sellDate to SellOrderContext(3, BigDecimal("2000.00")),
                    ),
                )
            )
        }
        coVerify(exactly = 1) {
            consolidator.calculateBondo(
                bondConsolidationContext(
                    bondOrderId = 1,
                    contributionDate = date2,
                    principal = BigDecimal("8000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = indexValues.associate {
                        it.date to YieldPercentageContext(floatingRateBond.value, it)
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
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate1 = LocalDate.parse("2024-01-15")
        val sellDate2 = LocalDate.parse("2024-01-20")

        val buyOrder = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = BigDecimal("10000.00")
        )

        val sellOrder1 = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate1,
            amount = BigDecimal("1500.00")
        )

        val sellOrder2 = BondOrder(
            id = 3,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate2,
            amount = BigDecimal("2500.00")
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
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { consolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        orchestrator.consolidateBy(bondId)

        coVerify {
            consolidator.calculateBondo(
                BondConsolidationContext(
                    bondOrderId = 1,
                    contributionDate = orderDate,
                    principal = BigDecimal("10000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = emptyMap(),
                    sellOrders = mapOf(
                        sellDate1 to SellOrderContext(2, BigDecimal("1500.00")),
                        sellDate2 to SellOrderContext(3, BigDecimal("2500.00"))
                    )
                )
            )
        }
    }

    "should filter out already redeemed buy orders from consolidation" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")

        val buyOrder1 = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = BigDecimal("5000.00")
        )

        val buyOrder2 = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = BigDecimal("8000.00")
        )

        val alreadyRedeemedBuyIds = setOf(1) // Order 1 is already fully redeemed

        val consolidationResult = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(2, orderDate, BigDecimal("30.00"))
            )
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns alreadyRedeemedBuyIds
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder1, buyOrder2)
        coEvery { repository.fetchLastByBondOrderId(2) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(2, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
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
                    contributionDate = orderDate,
                    principal = BigDecimal("8000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = emptyMap(),
                    sellOrders = emptyMap()
                )
            )
        }
    }

    "should filter out already consolidated sell orders from sell order mapping" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate1 = LocalDate.parse("2024-01-15")
        val sellDate2 = LocalDate.parse("2024-01-20")

        val buyOrder = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = BigDecimal("10000.00")
        )

        val sellOrder1 = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate1,
            amount = BigDecimal("1500.00")
        )

        val sellOrder2 = BondOrder(
            id = 3,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate2,
            amount = BigDecimal("2500.00")
        )

        val alreadyRedeemedBuyIds = emptySet<Int>()
        val consolidatedSellIds = setOf(2) // Only sell order 2 is consolidated

        val consolidationResult = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(1, sellDate2, BigDecimal("25.00")),
                BondOrderStatementCreation.PrincipalRedeem(1, sellDate2, BigDecimal("2500.00"), 3)
            )
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns alreadyRedeemedBuyIds
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns consolidatedSellIds
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder, sellOrder1, sellOrder2)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { consolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        orchestrator.consolidateBy(bondId)

        // Verify that only the non-consolidated sell order (id=3) is included in sell orders mapping
        coVerify(exactly = 1) { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) }
        coVerify {
            consolidator.calculateBondo(
                BondConsolidationContext(
                    bondOrderId = 1,
                    contributionDate = orderDate,
                    principal = BigDecimal("10000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = emptyMap(),
                    sellOrders = mapOf(
                        // Only sell order 3 should be present, sell order 2 is filtered out
                        sellDate2 to SellOrderContext(3, BigDecimal("2500.00"))
                    )
                )
            )
        }
    }

    "should handle scenario with both redeemed buys and consolidated sells filtered out" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate = LocalDate.parse("2024-01-15")

        val buyOrder1 = BondOrder(
            id = 1,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = BigDecimal("5000.00")
        )

        val buyOrder2 = BondOrder(
            id = 2,
            bond = floatingRateBond,
            type = BondOrderType.BUY,
            date = orderDate,
            amount = BigDecimal("7000.00")
        )

        val sellOrder1 = BondOrder(
            id = 3,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate,
            amount = BigDecimal("1000.00")
        )

        val sellOrder2 = BondOrder(
            id = 4,
            bond = floatingRateBond,
            type = BondOrderType.SELL,
            date = sellDate,
            amount = BigDecimal("2000.00")
        )

        val alreadyRedeemedBuyIds = setOf(1) // Buy order 1 is already redeemed
        val consolidatedSellIds = setOf(3) // Only sell order 3 is consolidated

        val consolidationResult = BondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                BondOrderStatementCreation.Yield(2, sellDate, BigDecimal("35.00")),
                BondOrderStatementCreation.PrincipalRedeem(2, sellDate, BigDecimal("2000.00"), 4)
            )
        )

        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns alreadyRedeemedBuyIds
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns consolidatedSellIds
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder1, buyOrder2, sellOrder1, sellOrder2)
        coEvery { repository.fetchLastByBondOrderId(2) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(2, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
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
                    contributionDate = orderDate,
                    principal = BigDecimal("7000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = emptyMap(),
                    sellOrders = mapOf(
                        sellDate to SellOrderContext(4, BigDecimal("2000.00"))
                    )
                )
            )
        }
    }
})
