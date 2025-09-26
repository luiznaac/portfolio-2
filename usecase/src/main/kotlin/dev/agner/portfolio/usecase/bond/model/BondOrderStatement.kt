package dev.agner.portfolio.usecase.bond.model

import kotlinx.datetime.LocalDate

data class BondOrderStatement(
    val id: Int,
    val bondOrderId: Int,
    val date: LocalDate,
    val amount: Double,
)

sealed class BondOrderStatementCreation(
    open val bondOrderId: Int,
    open val date: LocalDate,
    open val amount: Double,
) {
    data class Yield(
        override val bondOrderId: Int,
        override val date: LocalDate,
        override val amount: Double,
    ) : BondOrderStatementCreation(bondOrderId, date, amount)

    data class YieldRedeem(
        override val bondOrderId: Int,
        override val date: LocalDate,
        override val amount: Double,
        val sellBondOrderId: Int,
    ) : BondOrderStatementCreation(bondOrderId, date, amount)

    data class PrincipalRedeem(
        override val bondOrderId: Int,
        override val date: LocalDate,
        override val amount: Double,
        val sellBondOrderId: Int,
    ) : BondOrderStatementCreation(bondOrderId, date, amount)
}
