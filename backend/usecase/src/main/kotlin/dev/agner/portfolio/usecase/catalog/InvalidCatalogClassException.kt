package dev.agner.portfolio.usecase.catalog

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.DomainException

// A listed ticker is held as Ações or FIIs; Renda Fixa is the consolidation engine's domain, so a
// manual classification can never move a listed ticker there.
class InvalidCatalogClassException(assetClass: AssetClass) : DomainException(
    error = "invalid-catalog-class",
    userMessage = "A ticker cannot be classified in this asset class",
    detail = "Asset class $assetClass cannot classify a listed ticker",
)
