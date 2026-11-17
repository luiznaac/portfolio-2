package dev.agner.portfolio.usecase.corporateaction.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

// One brapi sync run before it is persisted: the window it covered, the counts it produced, the
// tickers that failed and the raw payloads it parsed. The raw body is stored with the audit so a
// later parser change never needs another paid month.
data class CorporateActionSyncCreation(
    val startedAt: LocalDateTime,
    val asOf: LocalDate,
    val windowFrom: LocalDate,
    val windowTo: LocalDate,
    val tickerCount: Int,
    val fetched: Int,
    val applied: Int,
    val parked: Int,
    val income: Int,
    val failedTickers: List<String>,
    val rawPayload: String,
)

// The report the sync action returns and the page shows: what the run fetched, applied, parked and
// failed, with the window it covered. The raw payload stays in the audit row, not the response.
data class CorporateActionSync(
    val id: Int,
    val startedAt: LocalDateTime,
    val asOf: LocalDate,
    val windowFrom: LocalDate,
    val windowTo: LocalDate,
    val tickerCount: Int,
    val fetched: Int,
    val applied: Int,
    val parked: Int,
    val income: Int,
    val failedTickers: List<String>,
)
