package dev.agner.portfolio.usecase.index.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class TheirIndexValue(
    val date: LocalDate,
    val value: BigDecimal,
) {
    fun toCreation() = IndexValueCreation(
        date = date,
        value = value,
    )
}
