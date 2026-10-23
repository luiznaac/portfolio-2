package dev.agner.portfolio.usecase.monthlyclose.model

import kotlinx.datetime.LocalDateTime

// The immutable record of a closed month: the month it belongs to and the whole close state frozen
// as data. A snapshot is written once, at the close, and never edited — a correction goes through
// the ledger and lands in the next month's snapshot, so history cannot be rewritten.
data class Snapshot(
    val id: Int,
    val monthId: Int,
    val state: CloseState,
    val createdAt: LocalDateTime,
)
