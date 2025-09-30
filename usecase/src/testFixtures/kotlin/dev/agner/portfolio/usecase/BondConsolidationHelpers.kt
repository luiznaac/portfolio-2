package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import kotlinx.datetime.LocalDate

fun bondCalculationContext(
    principal: Double,
    startingYield: Double,
    yieldPercentage: Double,
    sellAmount: Double = 0.0,
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
    principal: Double,
    yieldAmount: Double,
    yieldPercentages: Map<LocalDate, BondConsolidationContext.YieldPercentageContext>,
    sellOrders: Map<LocalDate, BondConsolidationContext.SellOrderContext> = emptyMap(),
    contributionDate: LocalDate = LocalDate.parse("2025-09-29"),
) = BondConsolidationContext(
    bondOrderId = bondOrderId,
    contributionDate = contributionDate,
    principal = principal,
    yieldAmount = yieldAmount,
    yieldPercentages = yieldPercentages,
    sellOrders = sellOrders,
)

fun floatingRateBond() = FloatingRateBond(
    id = int(),
    name = arbAsciiString(),
    value = double(),
    indexId = enum(),
)
