package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.DomainException
import java.math.BigDecimal

// Strategy Targets are shares of their class: the report slices may target the whole class (100%) but
// never over-plan it. The bucket carries no Target, so the remainder is derived rather than stored.
class StrategyTargetsExceedClassException(
    assetClass: AssetClass,
    percentage: BigDecimal,
) : DomainException(
    error = "strategy-targets-exceed-class",
    userMessage = "The strategy targets exceed 100% of the class",
    detail = "Strategy targets for $assetClass sum to $percentage%, above 100%",
)
