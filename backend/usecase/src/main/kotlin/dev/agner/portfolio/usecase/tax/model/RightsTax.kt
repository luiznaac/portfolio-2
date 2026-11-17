package dev.agner.portfolio.usecase.tax.model

import java.math.BigDecimal

// A subscription right's DARF 6015 line: the month's rights gain after its own carry bucket, at 15%.
// It is outside the R$20k stock meter and the FII bucket; the report carries it beside the rights
// realized row so the monthly bolsa DARF can state the right's share. A rights loss banks in the
// rights bucket and leaves the taxable gain at zero — cross-kind offset stays out of scope.
data class RightsTax(
    val rate: BigDecimal,
    val taxableGain: BigDecimal,
    val tax: BigDecimal,
) {
    companion object {
        // Lei 11.033/2004 art. 2º, I: a gain on a subscription right is 15%, filed under DARF 6015.
        val RATE: BigDecimal = BigDecimal("15.00")

        fun zero() = RightsTax(RATE, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}
