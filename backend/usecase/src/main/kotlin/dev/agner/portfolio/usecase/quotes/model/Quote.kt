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
