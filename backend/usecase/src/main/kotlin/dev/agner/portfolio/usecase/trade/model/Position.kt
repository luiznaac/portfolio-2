package dev.agner.portfolio.usecase.trade.model

import java.math.BigDecimal

/** The custody truth for one asset: what you hold and at what weighted average price. */
data class Position(
    val quantity: BigDecimal,
    val averagePrice: BigDecimal,
    val totalCost: BigDecimal,
)
