package dev.agner.portfolio.usecase.bond.position.model

import dev.agner.portfolio.usecase.bond.model.Bond
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class BondPosition(
    val bond: Bond,
    val date: LocalDate,
    val principal: BigDecimal,
    val yield: BigDecimal,
    val taxes: BigDecimal,
    val result: BigDecimal,
)
