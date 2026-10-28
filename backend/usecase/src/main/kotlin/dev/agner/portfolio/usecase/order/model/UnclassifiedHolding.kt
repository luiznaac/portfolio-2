package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

// A held ticker the catalog has not classified yet — a data-quality state, not a planning one. It
// counts in the Portfolio total but belongs to no class or Strategy rung, is never sold by Funding,
// and waits for classification; it is never reported as an outside-strategy holding. `amount` is the
// position valued at the latest close, absent when no quote prices it.
data class UnclassifiedHolding(
    val ticker: String,
    val quantity: BigDecimal,
    val price: BigDecimal?,
    val amount: BigDecimal?,
)
