package dev.agner.portfolio.usecase.tax.capitalgains

import dev.agner.portfolio.usecase.tax.capitalgains.model.MonthlyCapitalGain
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/** One realized sale, already stripped of which ticker it was — all this calculator needs. */
data class TaxableSale(
    val date: LocalDate,
    val isFii: Boolean,
    val isDayTrade: Boolean,
    val proceeds: BigDecimal,
    val costBasis: BigDecimal,
) {
    val gain: BigDecimal get() = proceeds - costBasis
}

/**
 * Pure: replays realized sales into the monthly gain/loss ledger the plan's Fase 5 calls for —
 * same lot-derived-from-replay spirit as [dev.agner.portfolio.usecase.trade.AveragePriceCalculator],
 * just one level up. Two independent buckets (stocks/ETFs/BDRs vs. FIIs — [MonthlyCapitalGain.isFii]),
 * each carrying its own loss forward, because Brazilian tax law never lets a stock loss offset a
 * FII gain or vice versa. The R$20k monthly exemption only covers non-day-trade sales: day-trade
 * proceeds never count toward the ceiling and a day-trade gain never lands in the exempt bucket.
 * Day-trade gains are still taxed in the 15% bucket here — the special 20% day-trade rate is left
 * for a later pass (see [dev.agner.portfolio.usecase.order.model.Order.dayTradeRisk]).
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

    private fun replay(
        isFii: Boolean,
        monthGroups: List<Pair<LocalDate, List<TaxableSale>>>,
    ): List<MonthlyCapitalGain> {
        var carriedLoss = BigDecimal.ZERO

        return monthGroups.map { (month, sales) ->
            val proceeds = sales.sumOf { it.proceeds }
            val grossGain = sales.sumOf { it.gain }
            val swingProceeds = sales.filter { !it.isDayTrade }.sumOf { it.proceeds }
            val exempt = !isFii && swingProceeds <= EXEMPTION_LIMIT
            // When the month is exempt, only the day-trade remainder is taxed; otherwise the whole
            // gross gain is. Either way a day-trade gain never ends up tax-free.
            val gainToTax = if (exempt) sales.filter { it.isDayTrade }.sumOf { it.gain } else grossGain

            val (compensation, taxable) = when {
                gainToTax <= BigDecimal.ZERO -> BigDecimal.ZERO to BigDecimal.ZERO
                else -> {
                    val used = gainToTax.min(carriedLoss)
                    used to (gainToTax - used)
                }
            }

            carriedLoss = if (grossGain < BigDecimal.ZERO) {
                carriedLoss + grossGain.negate()
            } else {
                carriedLoss - compensation
            }

            val rate = if (isFii) FII_RATE else STOCK_RATE

            MonthlyCapitalGain(
                month = month,
                isFii = isFii,
                proceeds = proceeds.setScale(2, RoundingMode.HALF_EVEN),
                grossGain = grossGain.setScale(2, RoundingMode.HALF_EVEN),
                exempt = exempt,
                lossCompensated = compensation.setScale(2, RoundingMode.HALF_EVEN),
                taxableGain = taxable.setScale(2, RoundingMode.HALF_EVEN),
                taxDue = (taxable * rate).setScale(2, RoundingMode.HALF_EVEN),
                lossCarriedForward = carriedLoss.setScale(2, RoundingMode.HALF_EVEN),
            )
        }
    }

    private fun monthOf(date: LocalDate) = LocalDate(date.year, date.month, 1)

    private companion object {
        val EXEMPTION_LIMIT: BigDecimal = BigDecimal("20000.00")
        val STOCK_RATE: BigDecimal = BigDecimal("0.15")
        val FII_RATE: BigDecimal = BigDecimal("0.20")
    }
}
