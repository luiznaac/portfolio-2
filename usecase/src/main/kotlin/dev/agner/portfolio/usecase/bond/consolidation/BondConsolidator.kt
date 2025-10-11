package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.PrincipalRedeem
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.TaxRedeem
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.Yield
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.YieldRedeem
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationResult
import dev.agner.portfolio.usecase.bond.consolidation.model.BondMaturityConsolidationContext
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation.TaxIncidence
import dev.agner.portfolio.usecase.commons.foldUntil
import dev.agner.portfolio.usecase.tax.TaxService
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class BondConsolidator(
    private val calculator: BondCalculator,
    private val taxService: TaxService,
) {

    suspend fun calculateBondo(consolidationContext: BondConsolidationContext): BondConsolidationResult =
        consolidationContext.dateRange
            .sorted()
            .foldUntil(
                IntermediateData(consolidationContext),
                { (ctx.principal + ctx.yieldAmount).compareTo(BigDecimal("0.00")) == 0 }
            ) { acc, date ->
                val result = calculator.calculate(
                    BondCalculationContext(
                        acc.ctx.principal,
                        acc.ctx.yieldAmount,
                        acc.ctx.yieldPercentages[date]?.percentage ?: BigDecimal("0.00"),
                        acc.ctx.sellOrders[date]?.amount ?: BigDecimal("0.00"),
                        taxService.getTaxIncidencesBy(date, acc.ctx.contributionDate),
                    ),
                    fullRedemption = acc.ctx.fullRedemption?.date == date,
                )

                acc.updateWith(result, date)
            }
            .run {
                BondConsolidationResult(
                    principal = ctx.principal,
                    yieldAmount = ctx.yieldAmount,
                    remainingSells = ctx.sellOrders,
                    statements = statements,
                )
            }

    suspend fun consolidateMaturity(ctx: BondMaturityConsolidationContext): List<BondOrderStatementCreation> {
        val result = calculator.calculate(
            BondCalculationContext(
                principal = ctx.principal,
                startingYield = ctx.yieldAmount,
                taxes = taxService.getTaxIncidencesBy(ctx.date, ctx.contributionDate),
            ),
            fullRedemption = true,
        )

        return result.statements.map {
            it.buildCreation(ctx.bondOrderId, ctx.maturityOrderId, ctx.date)
        }
    }

    private fun IntermediateData.updateWith(result: BondCalculationResult, date: LocalDate) =
        copy(
            ctx = ctx.copy(
                principal = result.principal,
                yieldAmount = result.yield,
                sellOrders = result.resolveSells(ctx, date)
            ),
            statements = statements + result.statements.map {
                it.buildCreation(ctx.bondOrderId, ctx.fullRedemption?.id ?: ctx.sellOrders[date]?.id, date)
            }
        )

    private fun BondCalculationResult.resolveSells(ctx: BondConsolidationContext, date: LocalDate) = when (this) {
        is BondCalculationResult.Ok -> ctx.sellOrders.filterKeys { it != date }
        is BondCalculationResult.RemainingRedemption -> {
            // Builds new sell order with the remaining amount
            val remainingSell = ctx.sellOrders[date]!!.copy(amount = remainingRedemptionAmount)
            ctx.sellOrders.filterKeys { it != date }
                .plus(date to remainingSell)
        }
    }

    private fun BondCalculationRecord.buildCreation(bondOrderId: Int, sellOrderId: Int?, date: LocalDate) =
        when (this) {
            is Yield -> BondOrderStatementCreation.Yield(bondOrderId, date, amount)
            is YieldRedeem -> {
                BondOrderStatementCreation.YieldRedeem(bondOrderId, date, amount, sellOrderId!!)
            }
            is PrincipalRedeem -> {
                BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date, amount, sellOrderId!!)
            }
            is TaxRedeem -> {
                TaxIncidence(bondOrderId, date, amount, sellOrderId!!, taxType)
            }
        }

    private data class IntermediateData(
        val ctx: BondConsolidationContext,
        val statements: List<BondOrderStatementCreation> = emptyList(),
    )
}
