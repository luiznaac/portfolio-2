package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import org.springframework.stereotype.Component

@Component
class BondCalculator {

    fun calculate(ctx: BondCalculationContext): BondCalculationResult {
        val yieldedAmount = ctx.calculateYield()

        return BondCalculationResult.Ok(
            principal = ctx.actualData.principal,
            yield = ctx.actualData.yieldAmount + yieldedAmount,
            statements = listOf(BondCalculationRecord.Yield(yieldedAmount))
        )
    }

    private fun BondCalculationContext.calculateYield() =
        (actualData.principal + actualData.yieldAmount) * processingData.yieldPercentage / 100
}
