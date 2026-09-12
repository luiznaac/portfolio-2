package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

// Stock sales (never FIIs — they have no exemption, always taxed at 20%) up to R$20,000/month
// are exempt from capital-gains tax; the ceiling is on the amount *sold*, not the gain. monthSold
// includes both already-executed trades this month and this plan's own pending SELL/FULL_EXIT orders,
// so the meter reflects what would happen if the whole plan were executed today.
data class SaleCeiling(
    val monthSold: BigDecimal,
    val limit: BigDecimal,
    val remaining: BigDecimal,
    val exceeded: Boolean,
)
