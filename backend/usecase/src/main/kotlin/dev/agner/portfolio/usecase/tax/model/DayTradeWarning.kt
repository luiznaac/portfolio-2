package dev.agner.portfolio.usecase.tax.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// A same-day both-sides pair at one institution: the guard's warning, never a tax computation. The
// matched quantity is what the day-trade rule identifies; the app reports it so the owner can see
// the pair and leaves the day-trade tax to a human.
data class DayTradeWarning(
    val ticker: String,
    val institutionId: Int,
    val date: LocalDate,
    val quantity: BigDecimal,
)
