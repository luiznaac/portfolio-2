package dev.agner.portfolio.usecase.tax

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

class TaxCalendarTest : StringSpec({

    "the DARF 6015 deadline is the last business day of the following month" {
        TaxCalendar.darfDueDate(YearMonth.parse("2026-10")) shouldBe LocalDate.parse("2026-11-30")
    }

    "a following month ending on a weekend moves the deadline back to Friday" {
        // October's last day, 31/10/2026, is a Saturday.
        TaxCalendar.darfDueDate(YearMonth.parse("2026-09")) shouldBe LocalDate.parse("2026-10-30")
        // February 2027 ends on a Sunday.
        TaxCalendar.darfDueDate(YearMonth.parse("2027-01")) shouldBe LocalDate.parse("2027-02-26")
    }

    "a deadline month ending on a weekday stays on its last day" {
        TaxCalendar.darfDueDate(YearMonth.parse("2027-04")) shouldBe LocalDate.parse("2027-05-31")
    }
})
