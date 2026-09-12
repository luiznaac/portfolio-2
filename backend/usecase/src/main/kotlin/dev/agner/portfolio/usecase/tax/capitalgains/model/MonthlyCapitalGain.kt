package dev.agner.portfolio.usecase.tax.capitalgains.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * One (month, asset group) bucket of the capital-gains ledger. [isFii] mirrors
 * [dev.agner.portfolio.usecase.order.model.Order.isFii] — FIIs have no sale exemption and are
 * always taxed at 20%. Everything else (stocks, ETFs, BDRs) shares the 15% rate, but only stocks
 * get the R$20,000/month exemption: [exempt] is true only for a month whose stock sales stayed
 * under the ceiling (day-trade sales never count). [month] is always the first day of the month.
 */
data class MonthlyCapitalGain(
    val month: LocalDate,
    val isFii: Boolean,
    val proceeds: BigDecimal,
    val grossGain: BigDecimal,
    val exempt: Boolean,
    val lossCompensated: BigDecimal,
    val taxableGain: BigDecimal,
    val taxDue: BigDecimal,
    // Loss carried into future months after this one's compensation — the running balance the
    // next month in the same bucket starts from.
    val lossCarriedForward: BigDecimal,
)
