package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

// A held ticker no Target names — a dropped ticker (weight 0 in the current Edition) or a position the
// chain never covered. The engine holds it and reports it for attention instead of emitting an
// automatic EXIT; a manual EXIT starts from `suggestedExitQuantity`, the whole-unit floor of the full
// position (D5). `amount` is the position valued at the latest close, absent when no quote prices it.
data class OutsideStrategyHolding(
    val ticker: String,
    val quantity: BigDecimal,
    val price: BigDecimal?,
    val amount: BigDecimal?,
    val suggestedExitQuantity: BigDecimal,
)
