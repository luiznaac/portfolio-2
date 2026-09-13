package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/** Scale of the principal/yield proportion used to split a partial redemption. */
private const val PROPORTION_SCALE = 6

/** Scale of the net/gross ratio used to gross a redeemed net amount back up. */
private const val RATIO_SCALE = 8

/** Scale of a tax rate carried as a percentage. */
private const val TAX_RATE_SCALE = 4

@Component
class BondCalculator {

    fun calculate(ctx: BondCalculationContext, fullRedemption: Boolean = false): BondCalculationResult {
        val yieldedAmount = ctx.calculateYield()
        val (redeemedPrincipal, redeemedYield, redeemedTaxes) = ctx.calculateRedemption(yieldedAmount, fullRedemption)

        val newPrincipal = (ctx.actualData.principal - redeemedPrincipal)
        val newYield = (ctx.actualData.yieldAmount + yieldedAmount - redeemedYield - redeemedTaxes)
        val statements = emptyList<BondCalculationRecord>()
            .plusIf(yieldedAmount > BigDecimal("0.00")) { BondCalculationRecord.YieldCalculation(yieldedAmount) }
            .plusIf(
                redeemedPrincipal > BigDecimal("0.00"),
            ) { BondCalculationRecord.PrincipalRedeemCalculation(redeemedPrincipal) }
            .plusIf(redeemedYield > BigDecimal("0.00")) { BondCalculationRecord.YieldRedeemCalculation(redeemedYield) }
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
        ((actualData.principal + actualData.yieldAmount) * processingData.yieldRate / BigDecimal("100"))
            .defaultScale()

    private fun BondCalculationContext.calculateRedemption(
        yieldedAmount: BigDecimal,
        fullRedemption: Boolean,
    ): RedemptionCalculation =
        if (!fullRedemption && processingData.redeemedAmount.compareTo(BigDecimal("0.00")) == 0) {
            RedemptionCalculation.zero()
        } else {
            with(actualData) {
                val grossYield = yieldAmount + yieldedAmount
                val netYield = processingData.taxes
                    .fold(grossYield) { acc, incidence ->
                        (acc * (BigDecimal("100.0000") - incidence.rate) / BigDecimal("100")).setScale(
                            2,
                            RoundingMode.HALF_EVEN,
                        )
                    }

                if (fullRedemption || principal + netYield <= processingData.redeemedAmount) {
                    RedemptionCalculation(
                        principal,
                        netYield,
                        processingData.taxes.calculate(netYield, netYield, grossYield),
                    )
                } else {
                    val proportion = principal.setScale(PROPORTION_SCALE) / (principal + netYield)
                    val redeemedPrincipal = (processingData.redeemedAmount * proportion).defaultScale()
                    val redeemedYield = (processingData.redeemedAmount * (BigDecimal.ONE - proportion)).setScale(
                        2,
                        RoundingMode.HALF_EVEN,
                    )

                    RedemptionCalculation(
                        redeemedPrincipal,
                        redeemedYield,
                        processingData.taxes.calculate(redeemedYield, netYield, grossYield),
                    )
                }
            }
        }
}

private fun Set<TaxIncidence>.calculate(
    redeemedNetAmount: BigDecimal,
    netAmount: BigDecimal,
    grossAmount: BigDecimal,
): Set<Pair<TaxIncidence, BigDecimal>> =
    if (isEmpty() || redeemedNetAmount == BigDecimal("0.00")) {
        emptySet()
    } else {
        val redeemedGrossAmount = ((redeemedNetAmount.setScale(RATIO_SCALE) / netAmount) * grossAmount).defaultScale()

        data class TaxState(val remainingAmount: BigDecimal, val results: Set<Pair<TaxIncidence, BigDecimal>>)

        foldIndexed(TaxState(redeemedGrossAmount, emptySet())) { idx, state, tax ->
            val consumedAmount = if (idx == size - 1) {
                // if it's the last tax, grab all the remaining amount to avoid rounding issues during calculation
                state.remainingAmount - redeemedNetAmount
            } else {
                (state.remainingAmount * tax.rate.setScale(TAX_RATE_SCALE) / BigDecimal("100")).defaultScale()
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

private data class RedemptionCalculation(
    val redeemedPrincipal: BigDecimal,
    val redeemedYield: BigDecimal,
    val redeemedTaxes: Set<Pair<TaxIncidence, BigDecimal>> = emptySet(),
) {
    companion object {
        fun zero() = RedemptionCalculation(BigDecimal("0.00"), BigDecimal("0.00"))
    }
}

private fun Pair<TaxIncidence, BigDecimal>.buildRecord() = BondCalculationRecord.TaxRedeemCalculation(
    amount = second,
    taxType = when (first) {
        is TaxIncidence.IOF -> "IOF"
        is TaxIncidence.Renda -> "RENDA"
    },
)
