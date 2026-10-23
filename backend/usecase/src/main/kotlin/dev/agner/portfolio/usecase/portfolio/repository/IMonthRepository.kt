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

    // Opens a month and closes any month still OPEN first, so the one-open-month invariant holds
    // without a caller having to remember it. Both writes share one transaction. A month closed this
    // way carries no Snapshot — only the explicit monthly close freezes one.
    suspend fun open(creation: MonthCreation): Month

    // Updates the open month's Capital and Floor; null when no month is open.
    suspend fun updateCurrent(update: MonthUpdate): Month?

    // Locks the month: OPEN -> CLOSED. Null when the month does not exist or is already closed, so
    // a close can never silently rewrite a locked month.
    suspend fun close(id: Int): Month?

    // Closed months, newest period first — the list history navigation starts from.
    suspend fun fetchClosed(): List<Month>
}
