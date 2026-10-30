package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.portfolio.model.MonthPeriodRange
import kotlinx.datetime.YearMonth

// Raised when the tax read is asked for a period outside the server-derived month window. The
// Impostos picker only offers in-window months; this is the defence against a hand-crafted request.
class TaxPeriodOutsideWindowException(
    val period: YearMonth,
    val range: MonthPeriodRange,
) : DomainException(
    error = "tax-period-outside-window",
    userMessage = "Tax period is outside the available window",
    detail = "Tax period $period is outside the window ${range.min}..${range.max}",
)
