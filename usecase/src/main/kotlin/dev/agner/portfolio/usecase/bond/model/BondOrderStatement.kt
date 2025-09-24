package dev.agner.portfolio.usecase.bond.model

import kotlinx.datetime.LocalDate

data class BondOrderStatement(
    val id: Int,
    val bondOrderId: Int,
    val date: LocalDate,
    val amount: Double,
)

data class BondOrderStatementCreation(
    val bondOrderId: Int,
    val date: LocalDate,
    val amount: Double,
)
