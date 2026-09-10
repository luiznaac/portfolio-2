package dev.agner.portfolio.usecase.strategy.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The strategy's own share of its AssetClass's ideal capital — e.g. within STOCKS, one strategy
// takes 40% and another 30%. Versioned by effectiveFrom, the same convention as AssetClassTarget:
// changing it is a dated fact, never an overwrite.
data class StrategyWeight(
    val id: Int,
    val strategyId: Int,
    val weight: BigDecimal,
    val effectiveFrom: LocalDate,
)

/**
 * What the client sends to set a weight. The owning strategy is *not* part of this shape — it
 * comes from the URL path and is passed alongside it to
 * [dev.agner.portfolio.usecase.strategy.StrategyService.setWeight].
 */
data class StrategyWeightCreation(
    val weight: BigDecimal,
    val effectiveFrom: LocalDate,
)
