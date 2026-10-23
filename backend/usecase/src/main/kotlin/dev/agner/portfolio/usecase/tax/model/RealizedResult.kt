package dev.agner.portfolio.usecase.tax.model

import java.math.BigDecimal

// One kind's realized result in the month. `gains` and `losses` are both positive magnitudes — the
// result is `gains - losses` — so a loss is never hidden as a negative gain. A day trade's gross is
// the matched same-day quantity's sale value, which is also what keeps it out of the stock-sale
// meter.
data class RealizedResult(
    val kind: RealizedKind,
    val grossSales: BigDecimal,
    val gains: BigDecimal,
    val losses: BigDecimal,
    val result: BigDecimal,
)
