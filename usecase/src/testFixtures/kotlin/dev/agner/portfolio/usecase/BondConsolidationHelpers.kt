package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.FullRedemptionContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.SellOrderContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationResult
import dev.agner.portfolio.usecase.bond.consolidation.model.BondMaturityConsolidationContext
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

fun bondCalculationContext(
    principal: BigDecimal,
    startingYield: BigDecimal,
    yieldPercentage: BigDecimal = BigDecimal("0.00"),
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
    sellOrders: Map<LocalDate, SellOrderContext> = emptyMap(),
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

fun floatingRateBond(
    maturityDate: LocalDate = LocalDate.parse("2025-09-29"),
) = FloatingRateBond(
    id = int(),
    name = arbAsciiString(),
    value = bigDecimal(),
    maturityDate = maturityDate,
    indexId = enum(),
)

fun bondConsolidationResult(
    remainingSells: Map<LocalDate, SellOrderContext> = emptyMap(),
    statements: List<BondOrderStatementCreation> = emptyList(),
    principal: BigDecimal = BigDecimal("0.00"),
    yieldAmount: BigDecimal = BigDecimal("0.00"),
) = BondConsolidationResult(
    remainingSells = remainingSells,
    statements = statements,
    principal = principal,
    yieldAmount = yieldAmount,
)

fun bondMaturityConsolidationContext(
    bondOrderId: Int = 1,
    maturityOrderId: Int = 100,
    date: LocalDate = LocalDate.parse("2024-06-30"),
    contributionDate: LocalDate = LocalDate.parse("2024-01-01"),
    principal: BigDecimal = BigDecimal("10000.00"),
    yieldAmount: BigDecimal = BigDecimal("500.00"),
) = BondMaturityConsolidationContext(
    bondOrderId = bondOrderId,
    maturityOrderId = maturityOrderId,
    date = date,
    contributionDate = contributionDate,
    principal = principal,
    yieldAmount = yieldAmount,
)
