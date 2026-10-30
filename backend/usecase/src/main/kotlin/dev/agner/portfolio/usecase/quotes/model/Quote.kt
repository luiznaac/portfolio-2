package dev.agner.portfolio.usecase.quotes.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// One EOD close for a ticker. The table is keyed on (ticker, date); the latest row is the current
// quote. COTAHIST is the authoritative source.
data class Quote(
    val ticker: String,
    val date: LocalDate,
    val close: BigDecimal,
    val source: QuoteSource,
)

enum class QuoteSource {
    COTAHIST,
}

// A close older than this many calendar days is stale: it is still a real price and stays in every
// priced sum, but it is shown with its date so an old quote never looks like a fresh one. The
// heuristic ignores exchange holidays deliberately — freshness is about the data, not the calendar.
const val QUOTE_STALE_DAYS = 5

fun Quote.isStale(today: LocalDate): Boolean =
    date.toEpochDays() < today.toEpochDays() - QUOTE_STALE_DAYS
