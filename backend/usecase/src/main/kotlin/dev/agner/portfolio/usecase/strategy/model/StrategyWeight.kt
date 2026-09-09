package dev.agner.portfolio.usecase.strategy.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The strategy's own share of its AssetClass's ideal capital — e.g. within ACOES, Top=40%,
// Dividendos=30%. Versioned by effectiveFrom, same convention as AssetClassTarget: changing it is
// a dated fact, never an overwrite.
data class StrategyWeight(
    val id: Int,
    val strategyId: Int,
    val weight: BigDecimal,
    val effectiveFrom: LocalDate,
)

// strategyId defaults to 0 because it's always overwritten from the URL path by the controller
// (POST /strategies/{strategy_id}/weight) — the frontend never sends it, same convention as
// TradeCreation/AttributionMovementCreation.
data class StrategyWeightCreation(
    val strategyId: Int = 0,
    val weight: BigDecimal,
    val effectiveFrom: LocalDate,
)
