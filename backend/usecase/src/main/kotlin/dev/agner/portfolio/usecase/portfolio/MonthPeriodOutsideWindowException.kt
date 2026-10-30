package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.portfolio.model.MonthPeriodRange
import kotlinx.datetime.YearMonth

// Raised when a caller asks to open a month the period window does not cover. The picker only
// offers in-window months; this is the defence against a stale or hand-crafted client.
class MonthPeriodOutsideWindowException(
    val period: YearMonth,
    val range: MonthPeriodRange,
) : DomainException(
    error = "month-period-outside-window",
    userMessage = "Month period is outside the available window",
    detail = "Month period $period is outside the window ${range.min}..${range.max}",
)
