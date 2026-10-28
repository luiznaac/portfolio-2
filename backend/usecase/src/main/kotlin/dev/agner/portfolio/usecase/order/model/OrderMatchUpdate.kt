package dev.agner.portfolio.usecase.order.model

// The match override's payload: the trade a suggestion should point at, or null to clear the
// override and return the suggestion to SUGGESTED.
data class OrderMatchUpdate(
    val tradeId: Int?,
)
