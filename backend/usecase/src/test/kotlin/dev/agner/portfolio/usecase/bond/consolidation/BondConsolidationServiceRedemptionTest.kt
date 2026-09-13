package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.RedemptionContext.SellContext
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullRedemption
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation.PrincipalRedeemCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation.YieldCreation
import dev.agner.portfolio.usecase.bondConsolidationResult
import dev.agner.portfolio.usecase.floatingRateBond
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

class BondConsolidationServiceRedemptionTest : StringSpec({

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
        coEvery { repository.saveAll(any()) } returns emptyList()

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
                    yieldRates = emptyMap(),
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
        coEvery { repository.saveAll(any()) } returns emptyList()

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
                    yieldRates = emptyMap(),
                    redemptionOrders = mapOf(
                        // Only sell order 3 should be present, sell order 2 is filtered out
                        sellDate2 to SellContext(3, BigDecimal("2500.00")),
                    ),
                ),
            )
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder)) }
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
        coEvery { repository.saveAll(any()) } returns emptyList()

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
                    yieldRates = emptyMap(),
                    redemptionOrders = mapOf(
                        sellDate to SellContext(4, BigDecimal("2000.00")),
                    ),
                ),
            )
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder2)) }
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
        coEvery { repository.saveAll(any()) } returns emptyList()
        coEvery { bondOrderService.updateType(2, FullRedemption::class) } just Runs

        consolidator.consolidate(consolidator.buildContext(bondId))

        coVerify(exactly = 1) { bondOrderService.updateType(2, FullRedemption::class) }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder)) }
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
        coVerify(exactly = 0) { positionService.consolidatePositions(any(), any()) }
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
        coEvery { repository.saveAll(any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(bondId))

        // Verify that no bond order updates occur when there are no remaining sells
        coVerify(exactly = 0) { bondOrderService.updateType(any(), FullRedemption::class) }
        coVerify(exactly = 1) { repository.saveAll(consolidationResult.statements) }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(buyOrder)) }
    }
})
