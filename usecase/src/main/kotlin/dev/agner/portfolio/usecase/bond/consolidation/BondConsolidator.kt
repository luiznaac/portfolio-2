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
        consolidationContext.yieldPercentages
            .keys.sorted()
            .foldUntil(
                IntermediateData(consolidationContext),
                { (ctx.principal + ctx.yieldAmount).compareTo(BigDecimal("0.00")) == 0 }
            ) { acc, date ->
                val result = calculator.calculate(
                    BondCalculationContext(
                        acc.ctx.principal,
                        acc.ctx.yieldAmount,
                        acc.ctx.yieldPercentages[date]!!.percentage,
                        acc.ctx.sellOrders[date]?.amount ?: BigDecimal("0.00"),
                        taxService.getTaxIncidencesBy(date, acc.ctx.contributionDate),
                    )
                )

                acc.updateWith(result, date)
            }
            .run {
                BondConsolidationResult(
                    remainingSells = ctx.sellOrders,
                    statements = statements,
                )
            }

    private fun IntermediateData.updateWith(result: BondCalculationResult, date: LocalDate) =
        copy(
            ctx = ctx.copy(
                principal = result.principal,
                yieldAmount = result.yield,
                sellOrders = result.resolveSells(ctx, date)
            ),
            statements = statements + result.statements.map { it.buildCreation(ctx, date) }
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

    private fun BondCalculationRecord.buildCreation(ctx: BondConsolidationContext, date: LocalDate) =
        when (this) {
            is Yield -> BondOrderStatementCreation.Yield(ctx.bondOrderId, date, amount)
            is YieldRedeem -> {
                BondOrderStatementCreation.YieldRedeem(ctx.bondOrderId, date, amount, ctx.sellOrders[date]!!.id)
            }
            is PrincipalRedeem -> {
                BondOrderStatementCreation.PrincipalRedeem(ctx.bondOrderId, date, amount, ctx.sellOrders[date]!!.id)
            }
            is TaxRedeem -> {
                TaxIncidence(ctx.bondOrderId, date, amount, ctx.sellOrders[date]!!.id, taxType)
            }
        }

    private data class IntermediateData(
        val ctx: BondConsolidationContext,
        val statements: List<BondOrderStatementCreation> = emptyList(),
    )
}
