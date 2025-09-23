package dev.agner.portfolio.usecase.bond.model

import kotlinx.datetime.LocalDate

data class BondOrderYield(
    val id: Int,
    val bondOrderId: Int,
    val date: LocalDate,
    val amount: Double,
)

data class BondOrderYieldCreation(
    val date: LocalDate,
    val amount: Double,
)
