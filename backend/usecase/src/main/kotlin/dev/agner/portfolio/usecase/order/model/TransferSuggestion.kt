package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

// Moving custody attribution between two strategies for the same ticker costs nothing — no
// brokerage, no tax, doesn't touch the sale-exemption ceiling — versus selling from one strategy
// and buying back for the other. See the plan's "Transferir antes de negociar". Computed fresh
// every time the plan is requested, not a persisted proposal with its own lifecycle (a
// simplification of the plan's fuller TransferProposal design — see the PR description).
data class TransferSuggestion(
    val listedAssetId: Int,
    val ticker: String,
    val fromStrategyId: Int,
    val fromStrategyName: String,
    val toStrategyId: Int,
    val toStrategyName: String,
    val quantity: BigDecimal,
)
