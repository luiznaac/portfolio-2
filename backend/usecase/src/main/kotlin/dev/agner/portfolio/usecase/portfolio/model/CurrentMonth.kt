package dev.agner.portfolio.usecase.portfolio.model

// The open month together with its target chain — the read the month-scoped screens are built on.
data class CurrentMonth(
    val month: Month,
    val targets: Targets,
)
