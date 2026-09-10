package dev.agner.portfolio.usecase.tax.capitalgains

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.tax.TaxRules
import dev.agner.portfolio.usecase.tax.capitalgains.model.MonthlyCapitalGain
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal

/** One realized sale, already stripped of which ticker it was — all this calculator needs. */
data class TaxableSale(
    val date: LocalDate,
    val isFii: Boolean,
    val proceeds: BigDecimal,
    val costBasis: BigDecimal,
) {
    val gain: BigDecimal get() = proceeds - costBasis
}

/**
 * Pure: replays realized sales into a monthly gain/loss ledger. Stocks (with ETFs and BDRs) and
 * FIIs are two independent buckets, each carrying its own loss forward, because a stock loss can
 * never offset a FII gain or the other way round.
 *
 * Day-trade gains are not taxed at the special day-trade rate here — the risk is flagged on the
 * order instead (see [dev.agner.portfolio.usecase.order.model.Order.dayTradeRisk]); folding it
 * into this calculator is left for a later pass.
 */
@Component
class CapitalGainsCalculator {

    fun calculate(sales: List<TaxableSale>): List<MonthlyCapitalGain> =
        sales
            .groupBy { it.isFii }
            .flatMap { (isFii, bucket) -> replay(isFii, bucket.groupBy { monthOf(it.date) }.toSortedMap()) }
            .sortedWith(compareBy({ it.month }, { it.isFii }))

    private fun replay(isFii: Boolean, salesByMonth: Map<LocalDate, List<TaxableSale>>): List<MonthlyCapitalGain> {
        var carriedLoss = BigDecimal.ZERO

        return salesByMonth.map { (month, sales) ->
            val proceeds = sales.sumOf { it.proceeds }
            val grossGain = sales.sumOf { it.gain }
            val exempt = !isFii && proceeds <= TaxRules.MONTHLY_STOCK_SALE_EXEMPTION

            // An exempt month is outside the regime entirely: its gain isn't taxed and its loss
            // isn't compensable, so neither touches the carried balance.
            val compensation = if (exempt || grossGain <= BigDecimal.ZERO) {
                BigDecimal.ZERO
            } else {
                grossGain.min(carriedLoss)
            }
            val taxable = if (exempt) BigDecimal.ZERO else (grossGain - compensation).max(BigDecimal.ZERO)

            carriedLoss = when {
                exempt -> carriedLoss
                grossGain < BigDecimal.ZERO -> carriedLoss + grossGain.negate()
                else -> carriedLoss - compensation
            }

            val rate = if (isFii) TaxRules.FII_CAPITAL_GAINS_RATE else TaxRules.STOCK_CAPITAL_GAINS_RATE

            MonthlyCapitalGain(
                month = month,
                isFii = isFii,
                proceeds = proceeds.defaultScale(),
                grossGain = grossGain.defaultScale(),
                exempt = exempt,
                lossCompensated = compensation.defaultScale(),
                taxableGain = taxable.defaultScale(),
                taxDue = (taxable * rate).defaultScale(),
                lossCarriedForward = carriedLoss.defaultScale(),
            )
        }
    }

    private fun monthOf(date: LocalDate) = LocalDate(date.year, date.month, 1)
}
