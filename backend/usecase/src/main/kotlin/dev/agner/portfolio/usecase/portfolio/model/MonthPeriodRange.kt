package dev.agner.portfolio.usecase.portfolio.model

import kotlinx.datetime.YearMonth

// The inclusive window a month may be planned in. `min` reaches back to imported history (or an
// existing month older than it); `max` never falls before the server's calendar month, so a closed
// month can never hide itself; `default` is the latest imported trade month clamped into the window,
// which is the month the opener pre-selects.
data class MonthPeriodRange(
    val min: YearMonth,
    val max: YearMonth,
    val default: YearMonth,
) {
    fun contains(period: YearMonth): Boolean = period in min..max
}
