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
    val isDayTrade: Boolean,
    // Only STOCK sales are eligible for the R$20k monthly exemption: ETFs and BDRs are always
    // taxed at 15%, FIIs at 20%.
    val exemptible: Boolean,
    val proceeds: BigDecimal,
    val costBasis: BigDecimal,
) {
    val gain: BigDecimal get() = proceeds - costBasis
}

/**
 * Pure: replays realized sales into a monthly gain/loss ledger — same lot-derived-from-replay
 * spirit as [dev.agner.portfolio.usecase.trade.AveragePriceCalculator], just one level up. Two
 * independent buckets (stocks/ETFs/BDRs vs. FIIs — [MonthlyCapitalGain.isFii]), each carrying its
 * own loss forward, because Brazilian tax law never lets a stock loss offset a FII gain or vice
 * versa.
 *
 * The R$20k monthly exemption covers STOCK sales only ([TaxableSale.exemptible]) and never
 * day-trade sales: day-trade proceeds do not count toward the ceiling and a day-trade gain never
 * lands in the exempt bucket. ETFs and BDRs are always taxed at 15%, FIIs at 20%. Day-trade gains
 * are still taxed in the 15% bucket — the special 20% day-trade rate is left for a later pass (see
 * [dev.agner.portfolio.usecase.order.model.Order.dayTradeRisk]).
 */
@Component
class CapitalGainsCalculator {

    fun calculate(sales: List<TaxableSale>): List<MonthlyCapitalGain> =
        sales
            .groupBy { it.isFii to monthOf(it.date) }
            .toList()
            .sortedBy { (key, _) -> key.second }
            .groupBy({ (key, _) -> key.first }, { (key, group) -> key.second to group })
            .flatMap { (isFii, monthGroups) -> replay(isFii, monthGroups.sortedBy { it.first }) }
            .sortedWith(compareBy({ it.month }, { it.isFii }))

    /**
     * Replays one bucket's months in chronological order, compensating each taxable month's gain
     * with the loss balance left by earlier ones.
     *
     * Losses only enter that balance from months that actually owed tax (`!exempt`): the R$20k
     * exemption is evaluated month by month, so a month at or under the ceiling has no tax due and
     * its loss has no future compensation to reduce — it does not carry. Within a taxed month the
     * negative result still reduces that same month's gain, since `grossGain` nets it before any
     * carryforward is computed.
     */
    private fun replay(
        isFii: Boolean,
        monthGroups: List<Pair<LocalDate, List<TaxableSale>>>,
    ): List<MonthlyCapitalGain> {
        var carriedLoss = BigDecimal.ZERO

        return monthGroups.map { (month, sales) ->
            val proceeds = sales.sumOf { it.proceeds }
            val grossGain = sales.sumOf { it.gain }
            val exemptibleSales = sales.filter { it.exemptible && !it.isDayTrade }
            val exemptibleProceeds = exemptibleSales.sumOf { it.proceeds }
            val exempt = !isFii && exemptibleSales.isNotEmpty() &&
                exemptibleProceeds <= TaxRules.MONTHLY_STOCK_SALE_EXEMPTION
            // Only the stock sales under the ceiling are exempt; every other gain in the bucket
            // (day trades, ETFs/BDRs, and the whole bucket once the ceiling is blown) is taxed.
            val gainToTax = grossGain - if (exempt) exemptibleSales.sumOf { it.gain } else BigDecimal.ZERO

            val (compensation, taxable) = when {
                gainToTax <= BigDecimal.ZERO -> BigDecimal.ZERO to BigDecimal.ZERO
                else -> {
                    val used = gainToTax.min(carriedLoss)
                    used to (gainToTax - used)
                }
            }

            carriedLoss = if (grossGain < BigDecimal.ZERO && !exempt) {
                carriedLoss + grossGain.negate()
            } else {
                carriedLoss - compensation
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
