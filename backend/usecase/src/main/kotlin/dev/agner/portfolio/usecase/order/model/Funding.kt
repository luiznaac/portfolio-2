package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

// Why a funding SELL exists, in the order the cascade prefers the sources (CONTEXT.md -> Funding):
// NEUTRO slices first, then positions outside every Strategy, then the largest Surpluses by R$.
enum class FundingSource {
    NEUTRO,
    OUTSIDE_STRATEGY,
    SURPLUS,
}

// The funding pass's arithmetic, exposed beside the candidates so the unfunded part of the plan is
// visible instead of silently missing. `buyAmount` is what the candidates spend; `sellAmount` is
// what the funding pass raises; `unfundedAmount` is the part no sell could cover. `neutroDeficit` is
// the R$ the NEUTRO constraint deliberately leaves visible and unfunded.
data class FundingSummary(
    val buyAmount: BigDecimal,
    val sellAmount: BigDecimal,
    val unfundedAmount: BigDecimal,
    val neutroDeficit: BigDecimal,
)

// The pro-rata pass's arithmetic, present on the full plan. When funding still leaves a shortfall,
// every buy is scaled by `factor` = deployable ÷ demand; `buyAmount` is what the scaled plan spends
// and `unfundedAmount` is the demand the scaling left out (the original shortfall plus the whole-unit
// rounding). A factor of 1 means the funding pass already covered every buy.
data class ProRataSummary(
    val originalBuyAmount: BigDecimal,
    val factor: BigDecimal,
    val buyAmount: BigDecimal,
    val unfundedAmount: BigDecimal,
)
