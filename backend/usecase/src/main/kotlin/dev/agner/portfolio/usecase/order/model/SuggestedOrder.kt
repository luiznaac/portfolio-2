package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

// The Suggested Order's lifecycle (CONTEXT.md -> Suggested Order). A suggestion starts SUGGESTED,
// reaches EXECUTED once the month's matched trades cumulatively cover its quantity, and IMPORTED
// when the execution it matched is on the ledger through a B3 import (or a manual trade B3 later
// confirmed) — the strongest evidence the order really happened.
enum class SuggestedOrderStatus {
    SUGGESTED,
    EXECUTED,
    IMPORTED,
}

// The lifecycle rule, shared by the bulk match and a single match override so the two can never
// disagree: cumulative executions short of the suggestion stay SUGGESTED, a covered suggestion is
// EXECUTED, and a covered one backed by an import is IMPORTED.
fun lifecycleStatus(
    quantity: BigDecimal,
    executedQuantity: BigDecimal,
    imported: Boolean,
): SuggestedOrderStatus = when {
    executedQuantity < quantity -> SuggestedOrderStatus.SUGGESTED
    imported -> SuggestedOrderStatus.IMPORTED
    else -> SuggestedOrderStatus.EXECUTED
}

// A persisted suggestion for the open month. `executedQuantity` is the cumulative matched quantity,
// shown beside the proposed one so a partial execution is visible without changing the lifecycle
// state. `matchedTradeId` is the first trade the match consumed — the evidence the order points at,
// and the one an override can retarget.
data class SuggestedOrder(
    val id: Int,
    val ticker: String,
    val side: OrderSide,
    val quantity: BigDecimal,
    val suggestedPrice: BigDecimal?,
    val executedQuantity: BigDecimal,
    val status: SuggestedOrderStatus,
    val matchedTradeId: Int?,
)

// The write shape — the order table's owner (the month) and the id are the repository's, never a
// field the caller could set.
data class SuggestedOrderCreation(
    val ticker: String,
    val side: OrderSide,
    val quantity: BigDecimal,
    val suggestedPrice: BigDecimal?,
    val executedQuantity: BigDecimal,
    val status: SuggestedOrderStatus,
    val matchedTradeId: Int?,
)
