package dev.agner.portfolio.usecase.index.model

import kotlinx.datetime.LocalDate

data class TheirIndexValue(
    val date: LocalDate,
    val value: Double,
) {
    fun toIndexValue() = IndexValue(
        date = date,
        value = value,
    )
}
