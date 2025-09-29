package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import org.springframework.stereotype.Component
import kotlin.math.min

@Component
class BondCalculator {

    fun calculate(ctx: BondCalculationContext): BondCalculationResult {
        val yieldedAmount = ctx.calculateYield()
        val (redeemedPrincipal, redeemedYield, redeemedTaxes) = ctx.calculateRedemption(yieldedAmount)

        val newPrincipal = (ctx.actualData.principal - redeemedPrincipal).toZeroIfTooSmall()
        val newYield = (ctx.actualData.yieldAmount + yieldedAmount - redeemedYield - redeemedTaxes).toZeroIfTooSmall()
        val statements = listOf(BondCalculationRecord.Yield(yieldedAmount))
            .plusIf(redeemedPrincipal > 0) { BondCalculationRecord.PrincipalRedeem(redeemedPrincipal) }
            .plusIf(redeemedYield > 0) { BondCalculationRecord.YieldRedeem(redeemedYield) }
            .plus(redeemedTaxes.map { it.buildRecord() })

        // Checking whether the total redeemed was enough to redeem the whole processing redeemed amount
        if ((ctx.processingData.redeemedAmount - (redeemedPrincipal + redeemedYield)).toZeroIfTooSmall() != 0.0) {
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
            val netYield = processingData.taxes
                .fold(yieldAmount + yieldedAmount) { acc, incidence ->
                    acc * (1 - incidence.rate / 100)
                }

            val proportion = principal / (principal + netYield)
            // The amount being redeemed can be greater than the total amount (principal + net yield),
            // so we must get the minimum between them
            val redeemingAmount = min(processingData.redeemedAmount, principal + netYield)
            val redeemedPrincipal = redeemingAmount * proportion
            val redeemedYield = redeemingAmount * (1 - proportion)

            return RedemptionCalculation(
                redeemedPrincipal,
                redeemedYield,
                processingData.taxes.calculate(redeemedYield),
            )
        }
    }
}

private fun Set<TaxIncidence>.calculate(redeemedNetAmount: Double): Set<Pair<TaxIncidence, Double>> {
    if (isEmpty()) return emptySet()

    // The actual formula is C = R / [Π (1 - txn)], where:
    //   - Π is the multiplication from 0 to n
    //   - n is the nth tax incidence
    //   - C is the redeemed gross amount
    val redeemedGrossAmount = redeemedNetAmount / map { 1 - (it.rate / 100) }.reduce { acc, rate -> acc * rate }

    data class TaxState(val remainingAmount: Double, val results: Set<Pair<TaxIncidence, Double>>)

    return fold(TaxState(redeemedGrossAmount, emptySet())) { state, tax ->
        val consumedAmount = state.remainingAmount * tax.rate / 100
        TaxState(
            remainingAmount = state.remainingAmount - consumedAmount,
            results = state.results + (tax to consumedAmount),
        )
    }.results
}

private operator fun Double.minus(taxes: Set<Pair<TaxIncidence, Double>>) = this - taxes.sumOf { it.second }

private inline fun <T> List<T>.plusIf(condition: Boolean, block: () -> T): List<T> =
    if (condition) this.plus(block()) else this

private fun Double.toZeroIfTooSmall() = if (this < 0.01) 0.0 else this

private data class RedemptionCalculation(
    val redeemedPrincipal: Double,
    val redeemedYield: Double,
    val redeemedTaxes: Set<Pair<TaxIncidence, Double>> = emptySet(),
) {
    companion object {
        fun zero() = RedemptionCalculation(0.0, 0.0)
    }
}

private fun Pair<TaxIncidence, Double>.buildRecord() = BondCalculationRecord.TaxRedeem(
    amount = second,
    taxType = when (first) {
        is TaxIncidence.IOF -> "IOF"
        is TaxIncidence.Renda -> "RENDA"
    }
)
