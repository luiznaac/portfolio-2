package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class BondCalculator {

    fun calculate(ctx: BondCalculationContext): BondCalculationResult {
        val yieldedAmount = ctx.calculateYield()
        val (redeemedPrincipal, redeemedYield, redeemedTaxes) = ctx.calculateRedemption(yieldedAmount)

        val newPrincipal = (ctx.actualData.principal - redeemedPrincipal)
        val newYield = (ctx.actualData.yieldAmount + yieldedAmount - redeemedYield - redeemedTaxes)
        val statements = listOf(BondCalculationRecord.Yield(yieldedAmount))
            .plusIf(redeemedPrincipal > BigDecimal("0.00")) { BondCalculationRecord.PrincipalRedeem(redeemedPrincipal) }
            .plusIf(redeemedYield > BigDecimal("0.00")) { BondCalculationRecord.YieldRedeem(redeemedYield) }
            .plus(redeemedTaxes.map { it.buildRecord() })

        // Checking whether the total redeemed was enough to redeem the whole processing redeemed amount
        if (ctx.processingData.redeemedAmount > redeemedPrincipal + redeemedYield) {
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
        ((actualData.principal + actualData.yieldAmount) * processingData.yieldPercentage / BigDecimal("100"))
            .defaultScale()

    private fun BondCalculationContext.calculateRedemption(yieldedAmount: BigDecimal): RedemptionCalculation {
        if (processingData.redeemedAmount == BigDecimal("0.00")) return RedemptionCalculation.zero()

        with(actualData) {
            val grossYield = yieldAmount + yieldedAmount
            val netYield = processingData.taxes
                .fold(grossYield) { acc, incidence ->
                    (acc * (BigDecimal("100.0000") - incidence.rate) / BigDecimal("100")).setScale(
                        2,
                        RoundingMode.HALF_EVEN
                    )
                }

            if (principal + netYield <= processingData.redeemedAmount) {
                return RedemptionCalculation(
                    principal,
                    netYield,
                    processingData.taxes.calculate(netYield, netYield, grossYield),
                )
            }

            val proportion = principal / (principal + netYield)
            val redeemedPrincipal = (processingData.redeemedAmount * proportion).defaultScale()
            val redeemedYield = (processingData.redeemedAmount * (BigDecimal.ONE - proportion)).setScale(
                2,
                RoundingMode.HALF_EVEN
            )

            return RedemptionCalculation(
                redeemedPrincipal,
                redeemedYield,
                processingData.taxes.calculate(redeemedYield, netYield, grossYield),
            )
        }
    }
}

private fun Set<TaxIncidence>.calculate(
    redeemedNetAmount: BigDecimal,
    netAmount: BigDecimal,
    grossAmount: BigDecimal,
): Set<Pair<TaxIncidence, BigDecimal>> {
    if (isEmpty()) return emptySet()

    val redeemedGrossAmount = ((redeemedNetAmount / netAmount) * grossAmount).defaultScale()

    data class TaxState(val remainingAmount: BigDecimal, val results: Set<Pair<TaxIncidence, BigDecimal>>)

    return foldIndexed(TaxState(redeemedGrossAmount, emptySet())) { idx, state, tax ->
        val consumedAmount = if (idx == size - 1) {
            // if it's the last tax, grab all the remaining amount to avoid rounding issues during calculation
            state.remainingAmount - redeemedNetAmount
        } else {
            (state.remainingAmount * tax.rate.setScale(4) / BigDecimal("100")).defaultScale()
        }

        TaxState(
            remainingAmount = state.remainingAmount - consumedAmount,
            results = state.results + (tax to consumedAmount),
        )
    }.results
}

private operator fun BigDecimal.minus(taxes: Set<Pair<TaxIncidence, BigDecimal>>) = this - taxes.sumOf { it.second }

private inline fun <T> List<T>.plusIf(condition: Boolean, block: () -> T): List<T> =
    if (condition) this.plus(block()) else this

private fun Double.toZeroIfTooSmall() = if (this < 0.01) 0.0 else this

private data class RedemptionCalculation(
    val redeemedPrincipal: BigDecimal,
    val redeemedYield: BigDecimal,
    val redeemedTaxes: Set<Pair<TaxIncidence, BigDecimal>> = emptySet(),
) {
    companion object {
        fun zero() = RedemptionCalculation(BigDecimal("0.00"), BigDecimal("0.00"))
    }
}

private fun Pair<TaxIncidence, BigDecimal>.buildRecord() = BondCalculationRecord.TaxRedeem(
    amount = second,
    taxType = when (first) {
        is TaxIncidence.IOF -> "IOF"
        is TaxIncidence.Renda -> "RENDA"
    }
)
