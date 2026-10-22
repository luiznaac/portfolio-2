package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

// The Attribution Transfer's decision state (CONTEXT.md -> Attribution Transfer). A suggestion is
// PENDING until a human approves or rejects it; below the auto-approval threshold the calculator
// decides it itself and records the APPROVED state with `autoApproved` as the trail.
enum class TransferStatus {
    PENDING,
    APPROVED,
    REJECTED,
}

// A persisted transfer proposal: bookkeeping only, no custody movement and no tax effect. The
// strategy names ride beside their ids so the inbox renders without a second lookup; `amount` is
// the quantity at the current quote — the R$ the move avoids selling — and is absent when no quote
// prices the ticker.
data class TransferProposal(
    val id: Int,
    val ticker: String,
    val fromStrategyId: Int,
    val fromStrategy: String,
    val toStrategyId: Int,
    val toStrategy: String,
    val quantity: BigDecimal,
    val amount: BigDecimal?,
    val autoApproved: Boolean,
    val status: TransferStatus,
)

// The write shape — the month and the id are the repository's, and the strategy ids are the
// proposal's own keys. `amount` is absent when no quote prices the ticker, which is also why such a
// proposal can never be auto-approved: there is no R$ value to compare against the threshold.
data class TransferProposalCreation(
    val ticker: String,
    val fromStrategyId: Int,
    val toStrategyId: Int,
    val quantity: BigDecimal,
    val amount: BigDecimal?,
    val autoApproved: Boolean,
    val status: TransferStatus,
)
