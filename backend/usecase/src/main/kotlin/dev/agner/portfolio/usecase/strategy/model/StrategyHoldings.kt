package dev.agner.portfolio.usecase.strategy.model

import java.math.BigDecimal

// A Strategy's attributed holdings, priced at the current quote — the read behind the read-only
// manual Strategy detail. The bucket total is the sum of the slices; `share` is each slice's
// percentage of that total.
data class StrategyHoldings(
    val strategyId: Int,
    val strategyName: String,
    val totalValue: BigDecimal,
    val holdings: List<StrategyHolding>,
)

data class StrategyHolding(
    val ticker: String,
    val quantity: BigDecimal,
    val value: BigDecimal,
    val share: BigDecimal,
)
