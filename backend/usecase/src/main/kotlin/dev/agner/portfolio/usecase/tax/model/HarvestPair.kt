package dev.agner.portfolio.usecase.tax.model

import java.math.BigDecimal

// An accepted harvest pair: the SELL and REBUY legs the owner confirmed, with edits, in the open
// month. `loss` is frozen at acceptance — the tax event the pair intends to realize — so a later
// position replay cannot rewrite it. Both legs stay editable in the request, never after the write:
// a different pair is a new acceptance.
data class HarvestPair(
    val id: Int,
    val ticker: String,
    val strategyId: Int?,
    val strategy: String?,
    val loss: BigDecimal,
    val sell: HarvestLeg,
    val rebuy: HarvestLeg,
)
