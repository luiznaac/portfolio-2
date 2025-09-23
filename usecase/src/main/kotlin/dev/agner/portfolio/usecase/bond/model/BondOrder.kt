package dev.agner.portfolio.usecase.bond.model

import kotlinx.datetime.LocalDate

enum class BondOrderType {
    BUY,
    SELL,
}

data class BondOrder(
    val id: Int,
    val bondId: Int,
    val type: BondOrderType,
    val date: LocalDate,
    val amount: Double,
)

data class BondOrderCreation(
    val bondId: Int,
    val type: BondOrderType,
    val date: LocalDate,
    val amount: Double,
)
