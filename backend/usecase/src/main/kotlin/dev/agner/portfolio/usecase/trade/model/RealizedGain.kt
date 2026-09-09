package dev.agner.portfolio.usecase.trade.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class RealizedGain(
    val tradeId: Int,
    val date: LocalDate,
    val quantity: BigDecimal,
    val proceeds: BigDecimal,
    val costBasis: BigDecimal,
) {
    val gain: BigDecimal get() = proceeds - costBasis
}
