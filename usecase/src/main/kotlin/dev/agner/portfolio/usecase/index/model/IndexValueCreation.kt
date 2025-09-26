package dev.agner.portfolio.usecase.index.model

import kotlinx.datetime.LocalDate

data class IndexValueCreation(
    val date: LocalDate,
    val value: Double,
)
