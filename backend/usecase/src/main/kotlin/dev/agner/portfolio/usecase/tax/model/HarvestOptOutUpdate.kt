package dev.agner.portfolio.usecase.tax.model

// The opt-out toggle's payload: true refuses harvest for the ticker, false clears the refusal.
data class HarvestOptOutUpdate(
    val optedOut: Boolean,
)
