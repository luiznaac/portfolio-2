package dev.agner.portfolio.usecase.bond.position.model

import dev.agner.portfolio.usecase.bond.model.Bond
import kotlinx.datetime.LocalDate

data class BondPosition(
    val bond: Bond,
    val date: LocalDate,
    val principal: Double,
    val yield: Double,
    val taxes: Double,
    val result: Double,
)
