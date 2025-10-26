
package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.DownToZeroContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.RedemptionContext.SellContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.YieldPercentageContext
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullRedemption
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement.Yield
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation.PrincipalRedeemCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation.YieldCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import dev.agner.portfolio.usecase.bondConsolidationContext
import dev.agner.portfolio.usecase.bondConsolidationResult
import dev.agner.portfolio.usecase.bondMaturityConsolidationContext
import dev.agner.portfolio.usecase.commons.nextDay
import dev.agner.portfolio.usecase.floatingRateBond
import dev.agner.portfolio.usecase.index.IndexValueService
import dev.agner.portfolio.usecase.index.model.IndexValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class BondConsolidationServiceTest : StringSpec({

    val repository = mockk<IBondOrderStatementRepository>()
    val bondOrderService = mockk<BondOrderService>()
    val indexValueService = mockk<IndexValueService>()
    val contributionConsolidator = mockk<BondContributionConsolidator>()
    val clock = mockk<Clock>()

    val service =
        BondConsolidationService(repository, bondOrderService, indexValueService, contributionConsolidator, clock)

    // Doing this so that I don't have to rewrite the whole test class and I ensure that the refactor hasn't broken anything
    val consolidator = BondConsolidator(repository, bondOrderService, service)

    beforeEach {
        clearAllMocks()
        every { clock.zone } returns ZoneId.systemDefault()
    }

    "should consolidate floating rate bond orders with buy and sell orders" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate = LocalDate.parse("2024-01-16")
        val lastStatementDate = LocalDate.parse("2024-01-15")
        val yesterdayDate = LocalDate.parse("2024-01-30")

        val buyOrder = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("10000.00"),
        )

        val sellOrder = BondOrder.Redemption.Sell(
            id = 2,
            bond = floatingRateBond,
            date = sellDate,
            amount = BigDecimal("1000.00"),
        )

        val fullRedemptionOrder = FullRedemption(
            id = 3,
            bond = floatingRateBond,
            date = sellDate,
        )

        val lastStatement = Yield(
            id = 1,
            buyOrderId = 1,
            date = lastStatementDate,
            amount = BigDecimal("0.00"),
        )

        val indexValues = listOf(
            IndexValue(date = sellDate, value = BigDecimal("100.00")),
        )

        val consolidationResult = bondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(1, sellDate, BigDecimal("50.00")),
                PrincipalRedeemCreation(1, sellDate, BigDecimal("1000.00"), 2),
            ),
        )

        every { clock.instant() } returns Instant.parse("2024-01-31T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder, sellOrder, fullRedemptionOrder)
        coEvery { repository.fetchLastByBondOrderId(1) } returns lastStatement
        coEvery { indexValueService.fetchAllBy(indexId, lastStatementDate.nextDay()) } returns indexValues
        coEvery {
            repository.sumUpConsolidatedValues(1, lastStatementDate.nextDay())
        } returns (BigDecimal("500.00") to BigDecimal("25.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 1) { bondOrderService.fetchByBondId(bondId) }
        coVerify(exactly = 1) { repository.fetchLastByBondOrderId(1) }
        coVerify(exactly = 1) { indexValueService.fetchAllBy(indexId, lastStatementDate.nextDay()) }
        coVerify(exactly = 1) { repository.sumUpConsolidatedValues(1, lastStatementDate.nextDay()) }
        coVerify(exactly = 1) {
            contributionConsolidator.calculateBondo(
                BondContributionConsolidationContext(
                    bondOrderId = 1,
                    contributionDate = orderDate,
                    dateRange = (lastStatementDate.nextDay()..yesterdayDate).mapNotNull {
                        it.takeIf { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
                    },
                    principal = BigDecimal("9500.00"),
                    yieldAmount = BigDecimal("25.00"),
                    yieldPercentages = mapOf(
                        sellDate to YieldPercentageContext(floatingRateBond.value, indexValues[0]),
                    ),
                    redemptionOrders = mapOf(
                        sellDate to SellContext(2, BigDecimal("1000.00")),
                    ),
                    downToZeroContext = DownToZeroContext(fullRedemptionOrder.id, fullRedemptionOrder.date),
                ),
            )
        }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
    }

    "should use order date when no previous statement exists" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-10")
        val yesterdayDate = LocalDate.parse("2024-01-30")

        val buyOrder = BondOrder.Contribution.Buy(
            id = 100,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("5000.00"),
        )

        val consolidationResult = bondConsolidationResult(
            remainingSells = emptyMap(),
            statements = emptyList(),
        )

        every { clock.instant() } returns Instant.parse("2024-01-31T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder)
        coEvery { repository.fetchLastByBondOrderId(100) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(100, orderDate) } returns
            (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify { repository.fetchLastByBondOrderId(100) }
        coVerify { indexValueService.fetchAllBy(indexId, orderDate) }
        coVerify { repository.sumUpConsolidatedValues(100, orderDate) }
        coVerify {
            contributionConsolidator.calculateBondo(
                BondContributionConsolidationContext(
                    bondOrderId = 100,
                    contributionDate = orderDate,
                    dateRange = (orderDate..yesterdayDate).mapNotNull {
                        it.takeIf { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
                    },
                    principal = BigDecimal("5000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = emptyMap(),
                    redemptionOrders = emptyMap(),
                ),
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
        val yesterdayDate = LocalDate.parse("2024-01-30")

        val buyOrder1 = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = date2, // Later date but added first
            amount = BigDecimal("8000.00"),
        )

        val buyOrder2 = BondOrder.Contribution.Buy(
            id = 2,
            bond = floatingRateBond,
            date = date1, // Earlier date
            amount = BigDecimal("5000.00"),
        )

        val sellOrder = BondOrder.Redemption.Sell(
            id = 3,
            bond = floatingRateBond,
            date = sellDate,
            amount = BigDecimal("2000.00"),
        )

        val indexValues = listOf(
            IndexValue(date = sellDate, value = BigDecimal("102.00")),
        )

        val consolidationResult1 = bondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(2, sellDate, BigDecimal("20.00")),
            ),
        )

        val consolidationResult2 = bondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(1, sellDate, BigDecimal("30.00")),
                PrincipalRedeemCreation(1, sellDate, BigDecimal("2000.00"), 3),
            ),
        )

        every { clock.instant() } returns Instant.parse("2024-01-31T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder1, buyOrder2, sellOrder)
        coEvery { repository.fetchLastByBondOrderId(2) } returns null // First order chronologically
        coEvery { repository.fetchLastByBondOrderId(1) } returns null // Second order chronologically
        coEvery { indexValueService.fetchAllBy(indexId, date1) } returns indexValues
        coEvery { indexValueService.fetchAllBy(indexId, date2) } returns indexValues
        coEvery { repository.sumUpConsolidatedValues(2, date1) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { repository.sumUpConsolidatedValues(1, date2) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery {
            contributionConsolidator.calculateBondo(any())
        } returnsMany listOf(consolidationResult1, consolidationResult2)
        coEvery { repository.saveAll(any()) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 1) {
            contributionConsolidator.calculateBondo(
                bondConsolidationContext(
                    bondOrderId = 2,
                    contributionDate = date1,
                    dateRange = (date1..yesterdayDate).mapNotNull {
                        it.takeIf { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
                    },
                    principal = BigDecimal("5000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = indexValues.associate {
                        it.date to YieldPercentageContext(floatingRateBond.value, it)
                    },
                    sellOrders = mapOf(
                        sellDate to SellContext(3, BigDecimal("2000.00")),
                    ),
                ),
            )
        }
        coVerify(exactly = 1) {
            contributionConsolidator.calculateBondo(
                bondConsolidationContext(
                    bondOrderId = 1,
                    contributionDate = date2,
                    dateRange = (date2..yesterdayDate).mapNotNull {
                        it.takeIf { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
                    },
                    principal = BigDecimal("8000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = indexValues.associate {
                        it.date to YieldPercentageContext(floatingRateBond.value, it)
                    },
                    sellOrders = emptyMap(),
                ),
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
        val yesterdayDate = LocalDate.parse("2024-01-30")

        val buyOrder = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("10000.00"),
        )

        val sellOrder1 = BondOrder.Redemption.Sell(
            id = 2,
            bond = floatingRateBond,
            date = sellDate1,
            amount = BigDecimal("1500.00"),
        )

        val sellOrder2 = BondOrder.Redemption.Sell(
            id = 3,
            bond = floatingRateBond,
            date = sellDate2,
            amount = BigDecimal("2500.00"),
        )

        val consolidationResult = bondConsolidationResult(
            remainingSells = emptyMap(),
            statements = emptyList(),
        )

        every { clock.instant() } returns Instant.parse("2024-01-31T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder, sellOrder1, sellOrder2)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify {
            contributionConsolidator.calculateBondo(
                BondContributionConsolidationContext(
                    bondOrderId = 1,
                    contributionDate = orderDate,
                    dateRange = (orderDate..yesterdayDate).mapNotNull {
                        it.takeIf { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
                    },
                    principal = BigDecimal("10000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = emptyMap(),
                    redemptionOrders = mapOf(
                        sellDate1 to SellContext(2, BigDecimal("1500.00")),
                        sellDate2 to SellContext(3, BigDecimal("2500.00")),
                    ),
                ),
            )
        }
    }

    "should filter out already redeemed buy orders from consolidation" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")
        val yesterdayDate = LocalDate.parse("2024-01-30")

        val buyOrder1 = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("5000.00"),
        )

        val buyOrder2 = BondOrder.Contribution.Buy(
            id = 2,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("8000.00"),
        )

        val alreadyRedeemedBuyIds = setOf(1) // Order 1 is already fully redeemed

        val consolidationResult = bondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(2, orderDate, BigDecimal("30.00")),
            ),
        )

        every { clock.instant() } returns Instant.parse("2024-01-31T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns alreadyRedeemedBuyIds
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder1, buyOrder2)
        coEvery { repository.fetchLastByBondOrderId(2) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(2, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        // Verify that only the non-redeemed buy order (id=2) is processed
        coVerify(exactly = 1) { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) }
        coVerify(exactly = 1) { repository.fetchLastByBondOrderId(2) }
        coVerify(exactly = 0) { repository.fetchLastByBondOrderId(1) } // Should not be called for redeemed order
        coVerify(exactly = 1) {
            contributionConsolidator.calculateBondo(
                BondContributionConsolidationContext(
                    bondOrderId = 2,
                    contributionDate = orderDate,
                    dateRange = (orderDate..yesterdayDate).mapNotNull {
                        it.takeIf { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
                    },
                    principal = BigDecimal("8000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = emptyMap(),
                    redemptionOrders = emptyMap(),
                ),
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
        val yesterdayDate = LocalDate.parse("2024-01-30")

        val buyOrder = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("10000.00"),
        )

        val sellOrder1 = BondOrder.Redemption.Sell(
            id = 2,
            bond = floatingRateBond,
            date = sellDate1,
            amount = BigDecimal("1500.00"),
        )

        val sellOrder2 = BondOrder.Redemption.Sell(
            id = 3,
            bond = floatingRateBond,
            date = sellDate2,
            amount = BigDecimal("2500.00"),
        )

        val alreadyRedeemedBuyIds = emptySet<Int>()
        val consolidatedSellIds = setOf(2) // Only sell order 2 is consolidated

        val consolidationResult = bondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(1, sellDate2, BigDecimal("25.00")),
                PrincipalRedeemCreation(1, sellDate2, BigDecimal("2500.00"), 3),
            ),
        )

        every { clock.instant() } returns Instant.parse("2024-01-31T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns alreadyRedeemedBuyIds
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns consolidatedSellIds
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder, sellOrder1, sellOrder2)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        // Verify that only the non-consolidated sell order (id=3) is included in sell orders mapping
        coVerify(exactly = 1) { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) }
        coVerify {
            contributionConsolidator.calculateBondo(
                BondContributionConsolidationContext(
                    bondOrderId = 1,
                    contributionDate = orderDate,
                    dateRange = (orderDate..yesterdayDate).mapNotNull {
                        it.takeIf { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
                    },
                    principal = BigDecimal("10000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = emptyMap(),
                    redemptionOrders = mapOf(
                        // Only sell order 3 should be present, sell order 2 is filtered out
                        sellDate2 to SellContext(3, BigDecimal("2500.00")),
                    ),
                ),
            )
        }
    }

    "should handle scenario with both redeemed buys and consolidated sells filtered out" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate = LocalDate.parse("2024-01-15")
        val yesterdayDate = LocalDate.parse("2024-01-30")

        val buyOrder1 = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("5000.00"),
        )

        val buyOrder2 = BondOrder.Contribution.Buy(
            id = 2,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("7000.00"),
        )

        val sellOrder1 = BondOrder.Redemption.Sell(
            id = 3,
            bond = floatingRateBond,
            date = sellDate,
            amount = BigDecimal("1000.00"),
        )

        val sellOrder2 = BondOrder.Redemption.Sell(
            id = 4,
            bond = floatingRateBond,
            date = sellDate,
            amount = BigDecimal("2000.00"),
        )

        val alreadyRedeemedBuyIds = setOf(1) // Buy order 1 is already redeemed
        val consolidatedSellIds = setOf(3) // Only sell order 3 is consolidated

        val consolidationResult = bondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(2, sellDate, BigDecimal("35.00")),
                PrincipalRedeemCreation(2, sellDate, BigDecimal("2000.00"), 4),
            ),
        )

        every { clock.instant() } returns Instant.parse("2024-01-31T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns alreadyRedeemedBuyIds
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns consolidatedSellIds
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder1, buyOrder2, sellOrder1, sellOrder2)
        coEvery { repository.fetchLastByBondOrderId(2) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(2, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        // Verify both filtering methods are called
        coVerify(exactly = 1) { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) }
        coVerify(exactly = 1) { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) }

        // Verify only non-redeemed buy order is processed
        coVerify(exactly = 1) { repository.fetchLastByBondOrderId(2) }
        coVerify(exactly = 0) { repository.fetchLastByBondOrderId(1) }

        // Verify only non-consolidated sell order is included
        coVerify {
            contributionConsolidator.calculateBondo(
                BondContributionConsolidationContext(
                    bondOrderId = 2,
                    contributionDate = orderDate,
                    dateRange = (orderDate..yesterdayDate).mapNotNull {
                        it.takeIf { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
                    },
                    principal = BigDecimal("7000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldPercentages = emptyMap(),
                    redemptionOrders = mapOf(
                        sellDate to SellContext(4, BigDecimal("2000.00")),
                    ),
                ),
            )
        }
    }

    "should update remaining sell order type to FULL_REDEMPTION when there is exactly one remaining sell" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate = LocalDate.parse("2024-01-15")

        val buyOrder = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("5000.00"),
        )

        val sellOrder = BondOrder.Redemption.Sell(
            id = 2,
            bond = floatingRateBond,
            date = sellDate,
            amount = BigDecimal("3000.00"), // Original amount
        )

        val remainingSellAfterConsolidation = SellContext(
            2,
            BigDecimal("500.00"),
        ) // Remaining amount after partial processing

        val consolidationResult = bondConsolidationResult(
            remainingSells = mapOf(sellDate to remainingSellAfterConsolidation),
            statements = listOf(
                YieldCreation(1, sellDate, BigDecimal("25.00")),
                PrincipalRedeemCreation(1, sellDate, BigDecimal("2500.00"), 2),
            ),
        )

        every { clock.instant() } returns Instant.parse("2024-01-31T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder, sellOrder)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs
        coEvery { bondOrderService.updateType(2, FullRedemption::class) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 1) { bondOrderService.updateType(2, FullRedemption::class) }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
    }

    "should throw IllegalStateException when there are multiple remaining sells" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate1 = LocalDate.parse("2024-01-15")
        val sellDate2 = LocalDate.parse("2024-01-20")

        val buyOrder = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("10000.00"),
        )

        val sellOrder1 = BondOrder.Redemption.Sell(
            id = 2,
            bond = floatingRateBond,
            date = sellDate1,
            amount = BigDecimal("2000.00"),
        )

        val sellOrder2 = BondOrder.Redemption.Sell(
            id = 3,
            bond = floatingRateBond,
            date = sellDate2,
            amount = BigDecimal("3000.00"),
        )

        // Consolidation result with multiple remaining sells (invalid scenario)
        val consolidationResult = bondConsolidationResult(
            remainingSells = mapOf(
                sellDate1 to SellContext(2, BigDecimal("500.00")),
                sellDate2 to SellContext(3, BigDecimal("1000.00")),
            ),
            statements = listOf(
                YieldCreation(1, sellDate1, BigDecimal("30.00")),
            ),
        )

        every { clock.instant() } returns Instant.parse("2024-01-31T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder, sellOrder1, sellOrder2)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult

        val exception = shouldThrow<IllegalStateException> {
            consolidator.consolidate(consolidator.buildContext(bondId))
        }

        exception.message shouldBe "There is more than one remaining sell"

        // Verify that no bond order updates or statement saves occur when exception is thrown
        coVerify(exactly = 0) { bondOrderService.updateType(any(), FullRedemption::class) }
        coVerify(exactly = 0) { repository.saveAll(any()) }
    }

    "should not update any sell order when there are no remaining sells" {
        val floatingRateBond = floatingRateBond()
        val bondId = floatingRateBond.id
        val indexId = floatingRateBond.indexId
        val orderDate = LocalDate.parse("2024-01-01")
        val sellDate = LocalDate.parse("2024-01-15")

        val buyOrder = BondOrder.Contribution.Buy(
            id = 1,
            bond = floatingRateBond,
            date = orderDate,
            amount = BigDecimal("5000.00"),
        )

        val sellOrder = BondOrder.Redemption.Sell(
            id = 2,
            bond = floatingRateBond,
            date = sellDate,
            amount = BigDecimal("2000.00"),
        )

        // Consolidation result with no remaining sells (all processed completely)
        val consolidationResult = bondConsolidationResult(
            remainingSells = emptyMap(),
            statements = listOf(
                YieldCreation(1, sellDate, BigDecimal("25.00")),
                PrincipalRedeemCreation(1, sellDate, BigDecimal("2000.00"), 2),
            ),
        )

        every { clock.instant() } returns Instant.parse("2024-01-31T10:00:00Z")
        coEvery { repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId) } returns emptySet()
        coEvery { repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId) } returns emptySet()
        coEvery { bondOrderService.fetchByBondId(bondId) } returns listOf(buyOrder, sellOrder)
        coEvery { repository.fetchLastByBondOrderId(1) } returns null
        coEvery { indexValueService.fetchAllBy(indexId, orderDate) } returns emptyList()
        coEvery { repository.sumUpConsolidatedValues(1, orderDate) } returns (BigDecimal("0.00") to BigDecimal("0.00"))
        coEvery { contributionConsolidator.calculateBondo(any()) } returns consolidationResult
        coEvery { repository.saveAll(any()) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        // Verify that no bond order updates occur when there are no remaining sells
        coVerify(exactly = 0) { bondOrderService.updateType(any(), FullRedemption::class) }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
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
                bondMaturityConsolidationContext(
                    bondOrderId = 1,
                    maturityOrderId = 100,
                    date = maturityDate,
                    contributionDate = orderDate,
                    principal = BigDecimal("10000.00"),
                    yieldAmount = BigDecimal("500.00"),
                ),
            )
        } returns maturityStatements
        coEvery { repository.saveAll(any()) } just Runs

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
                bondMaturityConsolidationContext(
                    bondOrderId = 1,
                    maturityOrderId = 100,
                    date = maturityDate,
                    contributionDate = orderDate,
                    principal = BigDecimal("10000.00"),
                    yieldAmount = BigDecimal("500.00"),
                ),
            )
        }
        coVerify(exactly = 1) {
            repository.saveAll(consolidationResult.statements + maturityStatements)
        }
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
        coEvery { repository.saveAll(any()) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 0) { bondOrderService.create(any(), any()) }
        coVerify(exactly = 0) { contributionConsolidator.consolidateMaturity(any()) }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
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
        coEvery { repository.saveAll(any()) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 0) { bondOrderService.create(any(), any()) }
        coVerify(exactly = 0) { contributionConsolidator.consolidateMaturity(any()) }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
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
        coEvery { repository.saveAll(any()) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 2) { bondOrderService.create(any(), any()) }
        coVerify(exactly = 2) { contributionConsolidator.consolidateMaturity(any()) }
        coVerify(exactly = 1) {
            repository.saveAll(
                consolidationResult1.statements + maturityStatements1 +
                    consolidationResult2.statements + maturityStatements2,
            )
        }
    }
})
