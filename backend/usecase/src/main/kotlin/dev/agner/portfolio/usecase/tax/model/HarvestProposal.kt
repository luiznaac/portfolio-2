package dev.agner.portfolio.usecase.tax.model

import java.math.BigDecimal

// One actionable harvest pair: sell a losing STOCK position on `sell.date` and rebuy it on the next
// business day. `loss` is positive — the amount realized by the sell at the position's fiscal
// average price. `strategyId`/`strategy` name the Attribution slice the units come from and return
// to; both are null when the position is unattributed. `neutro` records that the slice carries the
// "não operar" flag, which never blocks a harvest.
data class HarvestProposal(
    val ticker: String,
    val strategyId: Int?,
    val strategy: String?,
    val quantity: BigDecimal,
    val averagePrice: BigDecimal,
    val price: BigDecimal,
    val loss: BigDecimal,
    val neutro: Boolean,
    val sell: HarvestLeg,
    val rebuy: HarvestLeg,
)
