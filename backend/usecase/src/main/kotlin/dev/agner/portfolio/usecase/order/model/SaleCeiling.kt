package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

/**
 * The month's stock-sale exemption meter (see [dev.agner.portfolio.usecase.tax.TaxRules]). FIIs
 * are excluded — they have no exemption. [monthSold] counts both trades already executed this
 * month and this plan's own pending SELL/EXIT orders, so the meter reads as if the whole plan had
 * been executed today.
 */
data class SaleCeiling(
    val monthSold: BigDecimal,
    val limit: BigDecimal,
    val remaining: BigDecimal,
    val exceeded: Boolean,
)
