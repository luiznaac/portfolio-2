package dev.agner.portfolio.usecase.bond.position.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class BondPosition(
    val date: LocalDate,
    val principal: BigDecimal,
    val yield: BigDecimal,
    val taxes: BigDecimal,
)
