package dev.agner.portfolio.usecase.corporateaction.model

// Where a corporate action came from. The Movimentações export is the B3 feed; BRAPI is the paid
// one-off sync that fills the years the export does not cover. B3 stays authoritative on the same
// natural key, so the source also records which row the engine should keep.
enum class CorporateActionSource {
    B3_MOVEMENTS,
    BRAPI,
}
