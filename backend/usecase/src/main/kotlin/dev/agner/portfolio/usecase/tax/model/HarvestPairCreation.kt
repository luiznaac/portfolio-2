package dev.agner.portfolio.usecase.tax.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The acceptance payload. The ticker and legs are the caller's; the month and the row's id belong to
// the repository. `strategyId` pins the Attribution slice the rebuy returns to.
data class HarvestPairCreation(
    val ticker: String,
    val strategyId: Int?,
    val sellDate: LocalDate,
    val sellQuantity: BigDecimal,
    val sellPrice: BigDecimal,
    val rebuyDate: LocalDate,
    val rebuyQuantity: BigDecimal,
    val rebuyPrice: BigDecimal,
)
