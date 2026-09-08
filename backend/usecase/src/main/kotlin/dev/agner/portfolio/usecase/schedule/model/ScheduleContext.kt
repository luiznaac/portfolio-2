package dev.agner.portfolio.usecase.schedule.model

data class ScheduleContext(
    val host: String,
    val endpoint: String,
    val data: Any? = null,
)
