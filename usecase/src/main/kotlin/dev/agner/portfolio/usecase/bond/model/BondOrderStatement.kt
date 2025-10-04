package dev.agner.portfolio.usecase.bond.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class BondOrderStatement(
    val id: Int,
    val buyOrderId: Int,
    val date: LocalDate,
    val type: String,
    val amount: BigDecimal,
)

sealed class BondOrderStatementCreation(
    open val buyOrderId: Int,
    open val date: LocalDate,
    open val amount: BigDecimal,
) {
    data class Yield(
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
    ) : BondOrderStatementCreation(buyOrderId, date, amount)

    data class YieldRedeem(
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
        val sellOrderId: Int,
    ) : BondOrderStatementCreation(buyOrderId, date, amount)

    data class PrincipalRedeem(
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
        val sellOrderId: Int,
    ) : BondOrderStatementCreation(buyOrderId, date, amount)

    data class TaxIncidence(
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
        val sellOrderId: Int,
        val taxType: String,
    ) : BondOrderStatementCreation(buyOrderId, date, amount)
}
