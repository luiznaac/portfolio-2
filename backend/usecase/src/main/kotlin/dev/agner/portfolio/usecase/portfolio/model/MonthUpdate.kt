package dev.agner.portfolio.usecase.portfolio.model

import java.math.BigDecimal

// The editable half of an open month: Capital and the cash Floor. The period is fixed at open time,
// so it cannot be carried into an update.
data class MonthUpdate(
    val capital: BigDecimal,
    val floor: BigDecimal,
)
