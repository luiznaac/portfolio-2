package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.PrincipalRedeem
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.Yield
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.YieldRedeem
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationResult
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.extension.foldUntil
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

@Service
class BondConsolidator(
    private val calculator: BondCalculator,
) {

    suspend fun calculateBondo(consolidationContext: BondConsolidationContext): BondConsolidationResult =
        consolidationContext.yieldPercentages
            .keys.sorted() // TODO(): Calculate dates programmatically - doing this now because we must get holidays right
            .foldUntil(
                IntermediateData(consolidationContext),
                { ctx.principal + ctx.yieldAmount <= 0.01 }
            ) { acc, date ->
                val result = calculator.calculate(
                    BondCalculationContext(
                        acc.ctx.principal,
                        acc.ctx.yieldAmount,
                        acc.ctx.yieldPercentages[date]!!.percentage,
                        acc.ctx.sellOrders[date]?.amount ?: 0.0
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
        }

    private data class IntermediateData(
        val ctx: BondConsolidationContext,
        val statements: List<BondOrderStatementCreation> = emptyList(),
    )
}
