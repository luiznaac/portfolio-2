package dev.agner.portfolio.usecase.bond.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

enum class BondOrderType {
    BUY,
    SELL,
    FULL_REDEMPTION,
    MATURITY,
}

data class BondOrder(
    val id: Int,
    val bond: Bond,
    val type: BondOrderType,
    val date: LocalDate,
    val amount: BigDecimal = BigDecimal("0.00"),
)

data class BondOrderCreation(
    val bondId: Int,
    val type: BondOrderType,
    val date: LocalDate,
    val amount: BigDecimal = BigDecimal("0.00"),
)
