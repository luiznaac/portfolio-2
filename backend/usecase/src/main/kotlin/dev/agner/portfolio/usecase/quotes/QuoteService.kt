package dev.agner.portfolio.usecase.quotes

import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
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
    private val aliases: TickerAliasResolver,
) {

    // The paper's merged close series: every code it has traded under, oldest first. A rename does
    // not split the history, so a caller reading the old code and one reading the new see the same
    // series.
    suspend fun fetchAllBy(ticker: String): List<Quote> =
        aliases.aliasesOf(ticker)
            .flatMap { repository.fetchAllBy(it) }
            .sortedBy { it.date }

    // The "current quote" every valuation reads: the latest stored close with the date it belongs
    // to, null when the ticker has none yet. The rule lives here so the allocation tree and the
    // order plan cannot drift; the date lets a caller mark an old-but-real close as stale.
    suspend fun latestQuote(ticker: String): Quote? = fetchAllBy(ticker).maxByOrNull { it.date }

    suspend fun latestClose(ticker: String): BigDecimal? = latestQuote(ticker)?.close

    // The close a historical read needs: the last one on or before the requested date, merged across
    // the alias set. Without the merge a rename would make the new code look unpriced until its
    // first post-rename close, dropping performance to a stale or absent value.
    suspend fun latestQuoteOn(ticker: String, asOf: LocalDate): Quote? =
        fetchAllBy(ticker).filter { it.date <= asOf }.maxByOrNull { it.date }

    suspend fun latestCloseOn(ticker: String, asOf: LocalDate): BigDecimal? = latestQuoteOn(ticker, asOf)?.close

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
