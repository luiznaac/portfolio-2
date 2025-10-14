package dev.agner.portfolio.usecase.checkingaccount.position

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class CheckingAccountPosition(
    val date: LocalDate,
    val principal: BigDecimal,
    val yield: BigDecimal,
    val taxes: BigDecimal,
    val result: BigDecimal,
)
