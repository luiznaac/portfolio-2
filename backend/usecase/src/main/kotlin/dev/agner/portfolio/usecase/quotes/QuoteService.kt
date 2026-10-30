package dev.agner.portfolio.usecase.quotes

import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal

// What one historical year's upsert wrote: the row count plus the newest close date it stored, so
// the refresh can report an as-of even when the daily file was the part that failed.
data class MonthlyCloses(
    val count: Int,
    val latestDate: LocalDate?,
)

@Service
class QuoteService(
    private val repository: IQuoteRepository,
) {

    suspend fun fetchAllBy(ticker: String) = repository.fetchAllBy(ticker.normalizedTicker())

    // The "current quote" every valuation reads: the latest stored close, null when the ticker has
    // none yet. The rule lives here so the allocation tree and the order plan cannot drift.
    suspend fun latestClose(ticker: String): BigDecimal? =
        repository.fetchAllBy(ticker.normalizedTicker()).maxByOrNull { it.date }?.close

    // Store the COTAHIST EOD closes for the latest published day — the authoritative close once the
    // market has finished.
    suspend fun storeDailyCloses(rows: List<CotahistRow>): Int = repository.upsertAll(rows.map { it.toQuote() })

    // The historical range is stored as one close per ticker per month — the last trading day of
    // that month — for the ledger's tickers only. The annual file is read through a line reader, so
    // only the ledger tickers' rows are collected; the (ticker, date) key makes a re-run overwrite
    // rather than duplicate.
    suspend fun storeMonthlyCloses(rows: Flow<CotahistRow>, tickers: Set<String>): MonthlyCloses {
        val monthly = rows
            .filter { it.ticker in tickers }
            .toList()
            .groupBy { row -> row.ticker to (row.date.year to row.date.month) }
            .values
            .map { monthRows -> monthRows.maxBy { it.date } }
            .map { it.toQuote() }

        return MonthlyCloses(
            count = repository.upsertAll(monthly),
            latestDate = monthly.maxOfOrNull { it.date },
        )
    }

    private fun CotahistRow.toQuote() = Quote(
        ticker = ticker,
        date = date,
        close = closePrice,
        source = QuoteSource.COTAHIST,
    )
}
