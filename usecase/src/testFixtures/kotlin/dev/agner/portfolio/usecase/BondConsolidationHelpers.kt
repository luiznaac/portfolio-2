package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.FullRedemptionContext
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

fun bondCalculationContext(
    principal: BigDecimal,
    startingYield: BigDecimal,
    yieldPercentage: BigDecimal,
    sellAmount: BigDecimal = BigDecimal("0.00"),
    taxes: Set<TaxIncidence> = emptySet(),
) = BondCalculationContext(
    principal = principal,
    startingYield = startingYield,
    yieldPercentage = yieldPercentage,
    sellAmount = sellAmount,
    taxes = taxes,
)

fun bondConsolidationContext(
    bondOrderId: Int,
    principal: BigDecimal,
    yieldAmount: BigDecimal,
    yieldPercentages: Map<LocalDate, BondConsolidationContext.YieldPercentageContext>,
    sellOrders: Map<LocalDate, BondConsolidationContext.SellOrderContext> = emptyMap(),
    contributionDate: LocalDate = LocalDate.parse("2025-09-29"),
    dateRange: List<LocalDate> = emptyList(),
    fullRedemption: FullRedemptionContext? = null,
) = BondConsolidationContext(
    bondOrderId = bondOrderId,
    contributionDate = contributionDate,
    principal = principal,
    yieldAmount = yieldAmount,
    yieldPercentages = yieldPercentages,
    sellOrders = sellOrders,
    dateRange = dateRange,
    fullRedemption = fullRedemption,
)

fun floatingRateBond() = FloatingRateBond(
    id = int(),
    name = arbAsciiString(),
    value = bigDecimal(),
    indexId = enum(),
)
