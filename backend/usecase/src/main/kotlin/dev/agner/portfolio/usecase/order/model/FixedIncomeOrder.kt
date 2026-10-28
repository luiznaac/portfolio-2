package dev.agner.portfolio.usecase.order.model

import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import java.math.BigDecimal

// An RF sub-class order moves money without naming a product: the engine only knows the sub-class's
// Ideal and Actual, so it sizes a contribution or a redemption and the product is chosen when the
// order is executed (CONTEXT.md -> Suggested Order, D5).
enum class FixedIncomeOrderSide {
    CONTRIBUTE,
    REDEEM,
}

// One RF leaf order. `amount` is always positive R$; the side carries the direction.
data class FixedIncomeOrder(
    val subClass: FixedIncomeSubClass,
    val side: FixedIncomeOrderSide,
    val amount: BigDecimal,
)
