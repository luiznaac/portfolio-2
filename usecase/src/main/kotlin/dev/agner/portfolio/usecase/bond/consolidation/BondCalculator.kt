package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import org.springframework.stereotype.Component

@Component
class BondCalculator {

    // TODO(): Handle taxes
    fun calculate(ctx: BondCalculationContext): BondCalculationResult {
        val yieldedAmount = ctx.calculateYield()
        val (redeemedPrincipal, redeemedYield) = ctx.calculateRedemption(yieldedAmount)

        val newPrincipal = (ctx.actualData.principal - redeemedPrincipal).toZeroIfTooSmall()
        val newYield = (ctx.actualData.yieldAmount + yieldedAmount - redeemedYield).toZeroIfTooSmall()
        val statements = listOf(BondCalculationRecord.Yield(yieldedAmount))
            .plusIf(redeemedPrincipal > 0) { BondCalculationRecord.PrincipalRedeem(redeemedPrincipal) }
            .plusIf(redeemedYield > 0) { BondCalculationRecord.YieldRedeem(redeemedYield) }

        if (redeemedPrincipal + redeemedYield < ctx.processingData.redeemedAmount) {
            return BondCalculationResult.RemainingRedemption(
                principal = newPrincipal,
                yield = newYield,
                statements = statements,
                remainingRedemptionAmount = ctx.processingData.redeemedAmount - (redeemedPrincipal + redeemedYield),
            )
        }

        return BondCalculationResult.Ok(
            principal = newPrincipal,
            yield = newYield,
            statements = statements,
        )
    }

    private fun BondCalculationContext.calculateYield() =
        (actualData.principal + actualData.yieldAmount) * processingData.yieldPercentage / 100

    private fun BondCalculationContext.calculateRedemption(yieldedAmount: Double): RedemptionCalculation {
        if (processingData.redeemedAmount == 0.0) return RedemptionCalculation.zero()

        with(actualData) {
            if (principal + yieldAmount + yieldedAmount <= processingData.redeemedAmount) {
                return RedemptionCalculation(principal, yieldAmount + yieldedAmount)
            }

            val proportion = principal / (principal + yieldAmount + yieldedAmount)
            val redeemedPrincipal = processingData.redeemedAmount * proportion
            val redeemedYield = processingData.redeemedAmount * (1 - proportion)

            return RedemptionCalculation(redeemedPrincipal, redeemedYield)
        }
    }
}

private inline fun <T> List<T>.plusIf(condition: Boolean, block: () -> T): List<T> =
    if (condition) this.plus(block()) else this

private fun Double.toZeroIfTooSmall() = if (this < 0.01) 0.0 else this

private data class RedemptionCalculation(
    val redeemedPrincipal: Double,
    val redeemedYield: Double,
) {
    companion object {
        fun zero() = RedemptionCalculation(0.0, 0.0)
    }
}
