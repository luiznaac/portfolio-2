package dev.agner.portfolio.usecase.quotes

import dev.agner.portfolio.usecase.catalog.gateway.ICotahistSource
import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class QuoteService(
    private val repository: IQuoteRepository,
    private val cotahistSource: ICotahistSource,
) {

    suspend fun fetchAllBy(ticker: String) = repository.fetchAllBy(ticker.normalizedTicker())

    // The "current quote" every valuation reads: the latest stored close, null when the ticker has
    // none yet. The rule lives here so the allocation tree and the order plan cannot drift.
    suspend fun latestClose(ticker: String): BigDecimal? =
        repository.fetchAllBy(ticker.normalizedTicker()).maxByOrNull { it.date }?.close

    // Store the COTAHIST EOD closes for the latest published day — the authoritative close once the
    // market has finished.
    suspend fun refreshEod(): Int {
        val quotes = cotahistSource.fetchTradingDay().map {
            Quote(ticker = it.ticker, date = it.date, close = it.closePrice, source = QuoteSource.COTAHIST)
        }

        return repository.upsertAll(quotes)
    }
}
