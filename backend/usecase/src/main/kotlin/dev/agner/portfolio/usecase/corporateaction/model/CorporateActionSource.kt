package dev.agner.portfolio.usecase.corporateaction.model

// Where a corporate action came from. The Movimentações export is the external feed; DERIVED marks
// the engine's own fallback — the zero-cost rights allotment a sale with no receipt derives, which a
// real allotment replaces.
enum class CorporateActionSource {
    B3_MOVEMENTS,
    DERIVED,
}
