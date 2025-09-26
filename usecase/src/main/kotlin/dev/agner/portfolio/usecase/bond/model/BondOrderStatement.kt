package dev.agner.portfolio.usecase.bond.model

import kotlinx.datetime.LocalDate

data class BondOrderStatement(
    val id: Int,
    val buyOrderId: Int,
    val date: LocalDate,
    val amount: Double,
)

sealed class BondOrderStatementCreation(
    open val buyOrderId: Int,
    open val date: LocalDate,
    open val amount: Double,
) {
    data class Yield(
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: Double,
    ) : BondOrderStatementCreation(buyOrderId, date, amount)

    data class YieldRedeem(
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: Double,
        val sellOrderId: Int,
    ) : BondOrderStatementCreation(buyOrderId, date, amount)

    data class PrincipalRedeem(
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: Double,
        val sellOrderId: Int,
    ) : BondOrderStatementCreation(buyOrderId, date, amount)
}
