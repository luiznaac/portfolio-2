package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.commons.DomainException
import kotlinx.datetime.YearMonth

// Raised when opening a month whose period already has a month. The period is the month's natural
// key (the `month` table's unique index), so a duplicate is refused as a domain error instead of
// colliding on the raw constraint.
class MonthPeriodTakenException(period: YearMonth) : DomainException(
    error = "month-period-taken",
    userMessage = "A month for this period already exists",
    detail = "Month period $period already exists",
)
