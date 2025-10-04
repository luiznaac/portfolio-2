package dev.agner.portfolio.usecase.index.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class IndexValueCreation(
    val date: LocalDate,
    val value: BigDecimal,
)
