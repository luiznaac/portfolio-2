package dev.agner.portfolio.usecase.checkingaccount.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class CheckingAccountMovementCreation(
    val checkingAccountId: Int,
    val date: LocalDate,
    val amount: BigDecimal?,
)
