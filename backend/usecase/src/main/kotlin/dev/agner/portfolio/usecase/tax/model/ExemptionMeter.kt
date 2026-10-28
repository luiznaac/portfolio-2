package dev.agner.portfolio.usecase.tax.model

import java.math.BigDecimal

// The month-to-date stock-sale exemption meter. The exemption attaches to the month's total gross
// STOCK sales (Lei 11.033/2004 art. 3º, I): while they stay at R$20k or below, the month's gain is
// exempt. Crossing the limit is a cliff — the whole month's net gain becomes taxable at 15%, not
// just the part above the limit (Lei 11.033/2004 art. 59). `exceeded` is the cliff flag, and
// `taxableGain` is zero while exempt even when a gain was realized.
data class ExemptionMeter(
    val grossSales: BigDecimal,
    val exemptionLimit: BigDecimal,
    val exceeded: Boolean,
    val realizedGain: BigDecimal,
    val taxableGain: BigDecimal,
    val rate: BigDecimal,
    val tax: BigDecimal,
)
