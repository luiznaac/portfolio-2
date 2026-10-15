package dev.agner.portfolio.usecase.quotes.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// One EOD close for a ticker. The table is keyed on (ticker, date); "current quote" is simply the
// latest row. COTAHIST is authoritative for a closed day; brapi fills today's row before the
// COTAHIST file is published.
data class Quote(
    val ticker: String,
    val date: LocalDate,
    val close: BigDecimal,
    val source: QuoteSource,
)

enum class QuoteSource {
    COTAHIST,
    BRAPI,
}
