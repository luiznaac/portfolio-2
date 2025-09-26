package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import org.springframework.stereotype.Component

@Component
class BondCalculator {

    fun calculate(ctx: BondCalculationContext): BondCalculationResult {
        val yieldedAmount = ctx.calculateYield()
        val (redeemedPrincipal, redeemedYield) = ctx.calculateRedemption(yieldedAmount)

        return BondCalculationResult.Ok(
            principal = (ctx.actualData.principal - redeemedPrincipal).toZeroIfTooSmall(),
            yield = (ctx.actualData.yieldAmount + yieldedAmount - redeemedYield).toZeroIfTooSmall(),
            statements = listOf(BondCalculationRecord.Yield(yieldedAmount))
                .plusIf(redeemedPrincipal > 0) { BondCalculationRecord.PrincipalRedeem(redeemedPrincipal) }
                .plusIf(redeemedYield > 0) { BondCalculationRecord.YieldRedeem(redeemedYield) },
        )
    }

    private fun BondCalculationContext.calculateYield() =
        (actualData.principal + actualData.yieldAmount) * processingData.yieldPercentage / 100

    private fun BondCalculationContext.calculateRedemption(yieldedAmount: Double): Pair<Double, Double> {
        if (processingData.redeemedAmount == 0.0) return 0.0 to 0.0

        return with(actualData) {
            val proportion = principal / (principal + yieldAmount + yieldedAmount)
            val redeemedPrincipal = processingData.redeemedAmount * proportion
            val redeemedYield = processingData.redeemedAmount * (1 - proportion)

            redeemedPrincipal to redeemedYield
        }
    }
}

private inline fun <T> List<T>.plusIf(condition: Boolean, block: () -> T): List<T> =
    if (condition) this.plus(block()) else this

private fun Double.toZeroIfTooSmall() = if (this < 0.01) 0.0 else this
