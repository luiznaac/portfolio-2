package dev.agner.portfolio.usecase.listedasset.model

import com.fasterxml.jackson.annotation.JsonInclude
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// How the ledger narrows by ticker: the owner's typed search term (a substring) or the exact alias
// set a rename resolves the paper to. One field, two variants — a filter can never carry both and
// hide a precedence in the repository.
sealed interface TradeTickerFilter {
    data class Search(val text: String) : TradeTickerFilter

    data class Aliases(val tickers: List<String>) : TradeTickerFilter
}

// The ledger read's filter: a ticker match, an inclusive execution-date window and a side. `after`
// is the cursor of the last row already read, so paging never repeats or skips a row when executions
// share a date; `limit` bounds one page.
data class TradeLedgerFilter(
    val ticker: TradeTickerFilter? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val side: TradeSide? = null,
    val after: TradeLedgerCursor? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 50
    }
}

// The paging position: the (date, id) of the last row a page returned. The ledger orders by date
// descending and id descending, so this pair is the exact boundary of the next page.
data class TradeLedgerCursor(
    val date: LocalDate,
    val id: Int,
) {
    fun encode(): String = "$date:$id"

    companion object {
        // A malformed cursor is a caller error, never a guessed position: decoding returns null so the
        // controller can reject it with the canonical 400 instead of silently starting over.
        fun decode(raw: String): TradeLedgerCursor? {
            val parts = raw.split(":", limit = 2)
            if (parts.size != 2) return null
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
            val id = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return TradeLedgerCursor(date, id)
        }
    }
}

// One page of the ledger plus the two filter-wide aggregates that make the header honest while the
// pages load: `total` counts every matching row ("n de m") and `financialTotal` sums their absolute
// financial value, so the footer reflects the whole filter rather than only the loaded page. The
// cursor is always serialised, even as null: an absent key would read as "unknown" rather than "last
// page" to the client.
@JsonInclude(JsonInclude.Include.ALWAYS)
data class TradeLedgerPage(
    val rows: List<Trade>,
    val nextCursor: String?,
    val total: Int,
    val financialTotal: BigDecimal,
)
