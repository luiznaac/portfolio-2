package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.commons.weekdayOnOrBefore
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus

// The DARF 6015 deadline for a month's tax: the last business day of the following month. The app
// never files the DARF — it only surfaces when the month's tax is due. Weekends are the only
// non-business days known offline; a national holiday can move the deadline, so the UI states the
// date as an estimate beside the code.
object TaxCalendar {

    fun darfDueDate(period: YearMonth): LocalDate =
        period.plus(1, DateTimeUnit.MONTH).lastDay.weekdayOnOrBefore()
}
