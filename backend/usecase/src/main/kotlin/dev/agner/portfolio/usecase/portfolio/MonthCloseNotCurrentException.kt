package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.commons.DomainException
import kotlinx.datetime.YearMonth

// Raised when a backdated month is closed. A close freezes an immutable Snapshot valued at the
// latest close, so a past month would file today's book under a past period; backdated months are
// plan-only by design.
class MonthCloseNotCurrentException(period: YearMonth) : DomainException(
    error = "month-close-not-current",
    userMessage = "Only the current month can be closed",
    detail = "Month period $period is before the current calendar month",
)
