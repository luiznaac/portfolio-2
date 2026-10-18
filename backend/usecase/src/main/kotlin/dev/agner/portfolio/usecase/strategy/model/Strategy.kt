package dev.agner.portfolio.usecase.strategy.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import java.math.BigDecimal

// A named bucket of Listed-Assets money driven by an external report (CONTEXT.md -> Strategy); the
// five XP reports are the initial set. `weight` is the strategy's target share within its Asset
// Class — the number a StrategyTarget carries in a Month's chain.
data class Strategy(
    val id: Int,
    val name: String,
    val assetClass: AssetClass,
    val weight: BigDecimal,
)

// The creation shape: no id (the repository assigns it) and no editions.
data class StrategyCreation(
    val name: String,
    val assetClass: AssetClass,
    val weight: BigDecimal,
)
