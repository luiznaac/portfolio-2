package dev.agner.portfolio.usecase.tax.model

import java.math.BigDecimal

// One carry bucket's movement in the month. `openingBalance` is what the bucket carried into the
// month; `lossBanked` is this month's net loss added to it; `lossApplied` is the carry consumed by
// this month's taxable gains; `closingBalance` is what carries into the next month. A bucket is
// species-bound: swing STOCK, day trade and FII only ever offset gains of their own kind.
data class CarryBucket(
    val kind: RealizedKind,
    val openingBalance: BigDecimal,
    val lossBanked: BigDecimal,
    val lossApplied: BigDecimal,
    val closingBalance: BigDecimal,
)
