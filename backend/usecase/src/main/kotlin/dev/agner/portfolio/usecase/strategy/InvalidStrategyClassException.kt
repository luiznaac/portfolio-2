package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.DomainException

// Renda Fixa is the consolidation engine's domain: its chain carries sub-classes, never
// Strategies, so neither kind may be created there.
class InvalidStrategyClassException(assetClass: AssetClass) : DomainException(
    error = "invalid-strategy-class",
    userMessage = "A strategy cannot belong to this asset class",
    detail = "Asset class $assetClass cannot hold a strategy",
)
