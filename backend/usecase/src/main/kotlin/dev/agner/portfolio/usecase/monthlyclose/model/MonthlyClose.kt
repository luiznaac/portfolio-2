package dev.agner.portfolio.usecase.monthlyclose.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

enum class MonthlyCloseStatus {
    ABERTO,
    FECHADO,
}

/**
 * The month's close as a tiny state machine — ABERTO until you explicitly [MonthlyCloseStatus.FECHADO]
 * it. [month] is always the first day of the month. See the plan's Fase 7 "o fechamento como
 * objeto".
 */
data class MonthlyClose(
    val id: Int,
    val month: LocalDate,
    val status: MonthlyCloseStatus,
    val closedAt: LocalDateTime?,
)
