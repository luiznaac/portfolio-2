package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.PrincipalRedeemCalculation
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.TaxRedeemCalculation
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.YieldCalculation
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.YieldRedeemCalculation
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationResult
import dev.agner.portfolio.usecase.bond.consolidation.model.BondMaturityConsolidationContext
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation.TaxIncidenceCreation
import dev.agner.portfolio.usecase.commons.foldUntil
import dev.agner.portfolio.usecase.tax.TaxService
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class BondContributionConsolidator(
    private val calculator: BondCalculator,
    private val taxService: TaxService,
) {

    suspend fun calculateBondo(consolidationContext: BondContributionConsolidationContext) =
        consolidationContext.dateRange
            .sorted()
            .foldUntil(
                IntermediateData(consolidationContext),
                { (ctx.principal + ctx.yieldAmount).compareTo(BigDecimal("0.00")) == 0 },
            ) { acc, date ->
                val result = calculator.calculate(
                    BondCalculationContext(
                        acc.ctx.principal,
                        acc.ctx.yieldAmount,
                        acc.ctx.yieldRates[date]?.rate ?: BigDecimal("0.00"),
                        acc.ctx.redemptionOrders[date]?.amount ?: BigDecimal("0.00"),
                        taxService.getTaxIncidencesBy(date, acc.ctx.contributionDate),
                    ),
                    fullRedemption = acc.ctx.downToZeroContext?.date == date,
                )

                acc.updateWith(result, date)
            }
            .run {
                BondContributionConsolidationResult(
                    principal = ctx.principal,
                    yieldAmount = ctx.yieldAmount,
                    remainingSells = ctx.redemptionOrders,
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
                redemptionOrders = result.resolveSells(ctx, date),
            ),
            statements = statements + result.statements.map {
                it.buildCreation(ctx.bondOrderId, ctx.downToZeroContext?.id ?: ctx.redemptionOrders[date]?.id, date)
            },
        )

    private fun BondCalculationResult.resolveSells(ctx: BondContributionConsolidationContext, date: LocalDate) =
        when (this) {
            is BondCalculationResult.Ok -> ctx.redemptionOrders.filterKeys { it != date }
            is BondCalculationResult.RemainingRedemption -> {
                // Builds new sell order with the remaining amount
                val remainingSell = ctx.redemptionOrders[date]!!.copy(amount = remainingRedemptionAmount)
                ctx.redemptionOrders.filterKeys { it != date }
                    .plus(date to remainingSell)
            }
        }

    private fun BondCalculationRecord.buildCreation(bondOrderId: Int, sellOrderId: Int?, date: LocalDate) =
        when (this) {
            is YieldCalculation -> BondOrderStatementCreation.YieldCreation(bondOrderId, date, amount)
            is YieldRedeemCalculation -> {
                BondOrderStatementCreation.YieldRedeemCreation(bondOrderId, date, amount, sellOrderId!!)
            }
            is PrincipalRedeemCalculation -> {
                BondOrderStatementCreation.PrincipalRedeemCreation(bondOrderId, date, amount, sellOrderId!!)
            }
            is TaxRedeemCalculation -> {
                TaxIncidenceCreation(bondOrderId, date, amount, sellOrderId!!, taxType)
            }
        }

    private data class IntermediateData(
        val ctx: BondContributionConsolidationContext,
        val statements: List<BondOrderStatementCreation> = emptyList(),
    )
}
