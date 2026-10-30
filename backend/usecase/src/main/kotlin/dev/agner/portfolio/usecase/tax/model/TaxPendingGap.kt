package dev.agner.portfolio.usecase.tax.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// An open missing-sale GAP dated in the period whose proceeds are still unknown. It marks the
// month's result as pending; it never enters the tax arithmetic.
data class TaxPendingGap(
    val id: Int,
    val ticker: String,
    val date: LocalDate,
    val quantity: BigDecimal,
)
