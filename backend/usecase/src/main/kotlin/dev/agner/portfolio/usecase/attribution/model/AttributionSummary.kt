package dev.agner.portfolio.usecase.attribution.model

import java.math.BigDecimal

// The reconciliation view for the asset's page: custody (fiscal truth, from the trade ledger)
// against the sum of what the user has attributed to strategies. Σ balances == custody is the
// invariant; unattributed is the explicit bucket the plan requires instead of silent rateio.
data class AttributionSummary(
    val custodyQuantity: BigDecimal,
    val balances: List<StrategyBalance>,
) {
    val attributedQuantity: BigDecimal get() = balances.sumOf { it.quantity }
    val unattributedQuantity: BigDecimal get() = custodyQuantity - attributedQuantity
}

data class StrategyBalance(
    val strategyId: Int,
    val strategyName: String,
    val quantity: BigDecimal,
)
