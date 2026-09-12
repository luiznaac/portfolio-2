package dev.agner.portfolio.usecase.monthlyclose

import dev.agner.portfolio.usecase.commons.DomainException
import kotlinx.datetime.LocalDate

class MonthlyCloseAlreadyClosedException(month: LocalDate) :
    DomainException(
        error = "monthly-close-already-closed",
        userMessage = "This month is already closed",
        detail = "Month $month is already closed",
    )
