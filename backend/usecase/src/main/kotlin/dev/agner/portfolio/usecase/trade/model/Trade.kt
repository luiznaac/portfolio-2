package dev.agner.portfolio.usecase.trade.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/** A single execution. Positive [quantity] is a buy, negative is a sell — mirrors how a brokerage note reads. */
data class Trade(
    val id: Int,
    val assetId: Int,
    val date: LocalDate,
    val quantity: BigDecimal,
    val price: BigDecimal,
)
