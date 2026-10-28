package dev.agner.portfolio.usecase.corporateaction.model

// Where a corporate action came from. The Movimentações export is the only feed that carries
// events today.
enum class CorporateActionSource {
    B3_MOVEMENTS,
}
