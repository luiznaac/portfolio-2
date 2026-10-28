package dev.agner.portfolio.usecase.strategy.model

import java.math.BigDecimal

// How an Edition's entries differ from the previous active edition's: tickers that entered, those
// that left, and those whose weight or rating changed. A leaving ticker carries weight 0 — the
// report dropping it means "do not hold it", never "forget it" (CONTEXT.md -> Edition).
data class EditionDiff(
    val entered: List<DiffWeight>,
    val exited: List<DiffWeight>,
    val changed: List<DiffChange>,
)

data class DiffWeight(
    val ticker: String,
    val weight: BigDecimal,
)

data class DiffChange(
    val ticker: String,
    val weightBefore: BigDecimal,
    val weightAfter: BigDecimal,
    val ratingBefore: Rating?,
    val ratingAfter: Rating?,
)
