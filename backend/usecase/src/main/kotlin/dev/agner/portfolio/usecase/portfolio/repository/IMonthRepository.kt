package dev.agner.portfolio.usecase.portfolio.repository

import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.MonthUpdate
import kotlinx.datetime.YearMonth

interface IMonthRepository {

    // Null when no month is open.
    suspend fun fetchOpen(): Month?

    // Looked up by the natural key; null when no month has that period.
    suspend fun fetchByPeriod(period: YearMonth): Month?

    // Every month, open or closed — the period window derives its bounds from the earliest and
    // latest existing periods.
    suspend fun fetchAll(): List<Month>

    // Opens a month and replaces any month still OPEN, so the one-open-month invariant holds without
    // a caller having to remember it. A current open month is auto-closed without a Snapshot; a
    // backdated plan-only month is discarded with its targets. Both writes share one transaction.
    // Only the explicit monthly close ever freezes a Snapshot.
    suspend fun open(creation: MonthCreation): Month

    // Updates the open month's Capital and Floor; null when no month is open.
    suspend fun updateCurrent(update: MonthUpdate): Month?

    // Locks the month: OPEN -> CLOSED. Null when the month does not exist or is already closed, so
    // a close can never silently rewrite a locked month.
    suspend fun close(id: Int): Month?

    // Closed months, newest period first — the list history navigation starts from.
    suspend fun fetchClosed(): List<Month>
}
