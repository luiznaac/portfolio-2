package dev.agner.portfolio.usecase.corporateaction.model

// APPLIED is an event whose auto-math ran against the holder's position (or a ticker-only event
// whose remap ran); NEEDS_REVIEW is an event parked for a human decision with no ledger effect.
enum class CorporateActionStatus {
    APPLIED,
    NEEDS_REVIEW,
}
