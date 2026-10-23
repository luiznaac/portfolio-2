package dev.agner.portfolio.usecase.monthlyclose.repository

import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot

// The snapshot ledger. There is deliberately no update: a snapshot is written once at a month's
// close and is immutable afterwards (corrections go through the ledger into the next snapshot).
interface ISnapshotRepository {

    // Inserts the month's snapshot. A month that already has one is refused — the unique key is the
    // immutability guard, and a second write is a bug rather than an edit.
    suspend fun save(monthId: Int, state: CloseState): Snapshot

    suspend fun fetchByMonth(monthId: Int): Snapshot?

    // Every closed month's snapshot, oldest period first — the series the performance reads replay.
    suspend fun fetchAll(): List<Snapshot>
}
