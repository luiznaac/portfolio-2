package dev.agner.portfolio.usecase.index.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class TheirIndexValue(
    val date: LocalDate,
    val observation: BigDecimal,
) {
    fun toCreation() = IndexValueCreation(
        date = date,
        observation = observation,
    )
}
