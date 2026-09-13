package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.BondConsolidationContextFixture
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.DownToZeroContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.RedemptionContext.SellContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.YieldRateContext
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullRedemption
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement.Yield
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation.PrincipalRedeemCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation.YieldCreation
import dev.agner.portfolio.usecase.bondConsolidationResult
import dev.agner.portfolio.usecase.commons.nextDay
import dev.agner.portfolio.usecase.floatingRateBond
import dev.agner.portfolio.usecase.index.model.IndexValue
import io.kotest.core.spec.style.StringSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

class BondConsolidationServiceTest : StringSpec({

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
        coEvery { repository.saveAll(any()) } returns emptyList()

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
                    yieldRates = mapOf(
                        sellDate to YieldRateContext(floatingRateBond.value, indexValues[0]),
                    ),
                    redemptionOrders = mapOf(
                        sellDate to SellContext(2, BigDecimal("1000.00")),
                    ),
                    downToZeroContext = DownToZeroContext(fullRedemptionOrder.id, fullRedemptionOrder.date),
                ),
            )
        }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder)) }
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
        coEvery { repository.saveAll(any()) } returns emptyList()

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
                    yieldRates = emptyMap(),
                    redemptionOrders = emptyMap(),
                ),
            )
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder)) }
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
        coEvery { repository.saveAll(any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 1) {
            contributionConsolidator.calculateBondo(
                BondConsolidationContextFixture(
                    bondOrderId = 2,
                    contributionDate = date1,
                    dateRange = (date1..yesterdayDate).mapNotNull {
                        it.takeIf { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
                    },
                    principal = BigDecimal("5000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldRates = indexValues.associate {
                        it.date to YieldRateContext(floatingRateBond.value, it)
                    },
                    sellOrders = mapOf(
                        sellDate to SellContext(3, BigDecimal("2000.00")),
                    ),
                ).toContext(),
            )
        }
        coVerify(exactly = 1) {
            contributionConsolidator.calculateBondo(
                BondConsolidationContextFixture(
                    bondOrderId = 1,
                    contributionDate = date2,
                    dateRange = (date2..yesterdayDate).mapNotNull {
                        it.takeIf { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
                    },
                    principal = BigDecimal("8000.00"),
                    yieldAmount = BigDecimal("0.00"),
                    yieldRates = indexValues.associate {
                        it.date to YieldRateContext(floatingRateBond.value, it)
                    },
                    sellOrders = emptyMap(),
                ).toContext(),
            )
        }
        coVerify(exactly = 1) {
            repository.saveAll(consolidationResult1.statements + consolidationResult2.statements)
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder2, buyOrder1)) }
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
        coEvery { repository.saveAll(any()) } returns emptyList()

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
                    yieldRates = emptyMap(),
                    redemptionOrders = mapOf(
                        sellDate1 to SellContext(2, BigDecimal("1500.00")),
                        sellDate2 to SellContext(3, BigDecimal("2500.00")),
                    ),
                ),
            )
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder)) }
    }
})
