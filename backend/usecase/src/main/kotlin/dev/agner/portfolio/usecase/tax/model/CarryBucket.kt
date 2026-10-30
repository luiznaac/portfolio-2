package dev.agner.portfolio.usecase.tax.model

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal

// One carry bucket's movement in the month. `openingBalance` is what the bucket carried into the
// month; `lossBanked` is this month's net loss added to it; `lossApplied` is the carry consumed by
// this month's taxable gains; `closingBalance` is what carries into the next month. A bucket is
// species-bound: swing STOCK, day trade and FII only ever offset gains of their own kind.
//
// A seeded bucket has no meaningful history at or before its adoption boundary: the opening balance
// already represents those months, so the four numbers are null and `available` is false. A bucket
// with no seed starts at zero and is always available. The flat wire shape is the tax report and the
// frozen snapshot's contract; the invariant below keeps the Boolean and the nullable numbers from
// disagreeing, and the default keeps a snapshot written before this change readable as available.
@JsonInclude(JsonInclude.Include.ALWAYS)
data class CarryBucket(
    val kind: RealizedKind,
    val available: Boolean = true,
    val openingBalance: BigDecimal?,
    val lossBanked: BigDecimal?,
    val lossApplied: BigDecimal?,
    val closingBalance: BigDecimal?,
) {
    init {
        val numbers = listOf(openingBalance, lossBanked, lossApplied, closingBalance)
        require(numbers.all { it != null } || numbers.all { it == null }) {
            "Carry bucket $kind must carry either all four movements or none"
        }
        require(available == numbers.all { it != null }) {
            "Carry bucket $kind availability disagrees with its movements"
        }
    }
}
