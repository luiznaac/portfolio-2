package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.DownToZeroContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.RedemptionContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationResult
import dev.agner.portfolio.usecase.bond.consolidation.model.BondMaturityConsolidationContext
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

fun bondCalculationContext(
    principal: BigDecimal,
    startingYield: BigDecimal,
    yieldRate: BigDecimal = BigDecimal("0.00"),
    sellAmount: BigDecimal = BigDecimal("0.00"),
    taxes: Set<TaxIncidence> = emptySet(),
) = BondCalculationContext(
    principal = principal,
    startingYield = startingYield,
    yieldRate = yieldRate,
    sellAmount = sellAmount,
    taxes = taxes,
)

data class BondConsolidationContextFixture(
    val bondOrderId: Int,
    val principal: BigDecimal,
    val yieldAmount: BigDecimal,
    val yieldRates: Map<LocalDate, BondContributionConsolidationContext.YieldRateContext>,
    val sellOrders: Map<LocalDate, RedemptionContext> = emptyMap(),
    val contributionDate: LocalDate = LocalDate.parse("2025-09-29"),
    val dateRange: List<LocalDate> = emptyList(),
    val fullRedemption: DownToZeroContext? = null,
) {
    fun toContext() = BondContributionConsolidationContext(
        bondOrderId = bondOrderId,
        contributionDate = contributionDate,
        principal = principal,
        yieldAmount = yieldAmount,
        yieldRates = yieldRates,
        redemptionOrders = sellOrders,
        dateRange = dateRange,
        downToZeroContext = fullRedemption,
    )
}

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
    remainingSells: Map<LocalDate, RedemptionContext> = emptyMap(),
    statements: List<BondOrderStatementCreation> = emptyList(),
    principal: BigDecimal = BigDecimal("0.00"),
    yieldAmount: BigDecimal = BigDecimal("0.00"),
) = BondContributionConsolidationResult(
    remainingSells = remainingSells,
    statements = statements,
    principal = principal,
    yieldAmount = yieldAmount,
)

data class BondMaturityConsolidationContextFixture(
    val bondOrderId: Int = 1,
    val maturityOrderId: Int = 100,
    val date: LocalDate = LocalDate.parse("2024-06-30"),
    val contributionDate: LocalDate = LocalDate.parse("2024-01-01"),
    val principal: BigDecimal = BigDecimal("10000.00"),
    val yieldAmount: BigDecimal = BigDecimal("500.00"),
) {
    fun toContext() = BondMaturityConsolidationContext(
        bondOrderId = bondOrderId,
        maturityOrderId = maturityOrderId,
        date = date,
        contributionDate = contributionDate,
        principal = principal,
        yieldAmount = yieldAmount,
    )
}
