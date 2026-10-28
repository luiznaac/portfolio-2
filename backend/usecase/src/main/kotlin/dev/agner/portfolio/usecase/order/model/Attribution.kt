package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

// Which way an Attribution Move shifts units: ALLOCATE follows a BUY into the most-below-ideal
// slice first, DEALLOCATE follows a SELL out of the most-above-ideal slice first (D5).
enum class AttributionSide {
    ALLOCATE,
    DEALLOCATE,
}

// One suggested adjustment to the stored Attribution ledger, caused by a buy or a sell. It carries
// the (strategy, ticker) slice and a positive quantity; the side says whether the slice gains or
// loses units. Bookkeeping only — no trade, no custody movement.
data class AttributionMove(
    val ticker: String,
    val strategy: String,
    val side: AttributionSide,
    val quantity: BigDecimal,
)

// A bookkeeping move between two slices of the same ticker: the slice above its whole-unit Target
// gives units to the one below it, without touching custody or taxes (CONTEXT.md -> Attribution
// Transfer). A Manual Strategy is a target-less donor — it has no Ideal, so it may give from its
// attributed units. `amount` values the moved units at the latest close; it is absent when no quote
// prices the ticker.
data class AttributionTransfer(
    val ticker: String,
    val fromStrategy: String,
    val toStrategy: String,
    val quantity: BigDecimal,
    val price: BigDecimal?,
    val amount: BigDecimal?,
)
