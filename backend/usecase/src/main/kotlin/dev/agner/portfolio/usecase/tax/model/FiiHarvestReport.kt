package dev.agner.portfolio.usecase.tax.model

import java.math.BigDecimal

// A held FII's harvest picture. FIIs are report-only: they have no monthly exemption to fill, their
// gains are always taxed at 20%, and their losses may only offset other FII gains, so the app shows
// the estimated liability instead of proposing a pair. `unrealizedResult` is positive for a gain and
// negative for a loss; `estimatedTax` is 20% of a gain and zero on a loss.
data class FiiHarvestReport(
    val ticker: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val averagePrice: BigDecimal,
    val unrealizedResult: BigDecimal,
    val estimatedTax: BigDecimal,
)
