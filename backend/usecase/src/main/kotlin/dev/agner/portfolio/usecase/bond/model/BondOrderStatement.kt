package dev.agner.portfolio.usecase.bond.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

sealed class BondOrderStatement(
    open val id: Int,
    open val buyOrderId: Int,
    open val date: LocalDate,
    open val amount: BigDecimal,
) {
    data class Yield(
        override val id: Int,
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
    ) : BondOrderStatement(id, buyOrderId, date, amount)

    data class YieldRedeem(
        override val id: Int,
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
        val sellOrderId: Int,
    ) : BondOrderStatement(id, buyOrderId, date, amount)

    data class PrincipalRedeem(
        override val id: Int,
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
        val sellOrderId: Int,
    ) : BondOrderStatement(id, buyOrderId, date, amount)

    data class TaxIncidence(
        override val id: Int,
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
        val sellOrderId: Int,
        val taxType: String,
    ) : BondOrderStatement(id, buyOrderId, date, amount)
}

sealed class BondOrderStatementCreation(
    open val buyOrderId: Int,
    open val date: LocalDate,
    open val amount: BigDecimal,
) {
    data class YieldCreation(
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
    ) : BondOrderStatementCreation(buyOrderId, date, amount)

    data class YieldRedeemCreation(
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
        val sellOrderId: Int,
    ) : BondOrderStatementCreation(buyOrderId, date, amount)

    data class PrincipalRedeemCreation(
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
        val sellOrderId: Int,
    ) : BondOrderStatementCreation(buyOrderId, date, amount)

    data class TaxIncidenceCreation(
        override val buyOrderId: Int,
        override val date: LocalDate,
        override val amount: BigDecimal,
        val sellOrderId: Int,
        val taxType: String,
    ) : BondOrderStatementCreation(buyOrderId, date, amount)
}
