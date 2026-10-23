package dev.agner.portfolio.usecase.tax.model

import java.math.BigDecimal

// A loss-making STOCK position the owner opted out of harvesting. It is still reported — with the
// whole available quantity and the loss it would bank — so the opt-out is a visible choice that can
// be reversed, never a silently dropped ticker.
data class HarvestIgnored(
    val ticker: String,
    val strategyId: Int?,
    val strategy: String?,
    val quantity: BigDecimal,
    val loss: BigDecimal,
    val neutro: Boolean,
)
