package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.DomainException

// A class has at most one legacy bucket: a second MANUAL Strategy in the same class would split
// the legacy book and make the class Actual ambiguous.
class DuplicateManualStrategyException(assetClass: AssetClass, name: String) : DomainException(
    error = "duplicate-manual-strategy",
    userMessage = "This asset class already has a manual strategy",
    detail = "Asset class $assetClass already has the manual strategy '$name'",
)
