package dev.agner.portfolio.usecase.index.model

import kotlinx.datetime.LocalDate

data class TheirIndexValue(
    val date: LocalDate,
    val value: Double,
) {
    fun toCreation() = IndexValueCreation(
        date = date,
        value = value,
    )
}
