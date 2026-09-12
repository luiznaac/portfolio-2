package dev.agner.portfolio.usecase.monthlyclose.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

enum class MonthlyCloseStatus {
    OPEN,
    CLOSED,
}

/** The month's close as a tiny state machine. [month] is always the first day of the month. */
data class MonthlyClose(
    val id: Int,
    val month: LocalDate,
    val status: MonthlyCloseStatus,
    val closedAt: LocalDateTime?,
)
