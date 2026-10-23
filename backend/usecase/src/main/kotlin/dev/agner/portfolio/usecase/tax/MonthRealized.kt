package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.tax.model.RealizedResult
import kotlinx.datetime.YearMonth

// One month's realized results plus the month-level exemption verdict the carry roll needs. `swing`
// is carried alongside the list because it is the only kind the meter reads; keeping it here means
// the result set is scanned once, when the month is built.
internal data class MonthRealized(
    val period: YearMonth,
    val results: List<RealizedResult>,
    val swing: RealizedResult,
    val swingExceeded: Boolean,
)
