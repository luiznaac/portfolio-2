package dev.agner.portfolio.usecase.portfolio.model

import java.math.BigDecimal

// The editable half of an open month: Capital, the cash Floor and the month's net Aportes. The
// period is fixed at open time, so it cannot be carried into an update. A null aportes leaves the
// recorded value untouched (the pre-aportes clients update only Capital and Floor).
data class MonthUpdate(
    val capital: BigDecimal,
    val floor: BigDecimal,
    val aportes: BigDecimal? = null,
)
