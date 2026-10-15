package dev.agner.portfolio.persistence.quotes

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class QuoteRepository(
    private val clock: Clock,
) : IQuoteRepository {

    // Keyed on (ticker, date): a re-sync of the same day overwrites the row (COTAHIST is
    // authoritative).
    override suspend fun upsertAll(quotes: List<Quote>): Int = transaction {
        if (quotes.isEmpty()) return@transaction 0

        QuoteTable.batchUpsert(
            quotes,
            QuoteTable.ticker,
            QuoteTable.date,
            onUpdateExclude = listOf(QuoteTable.createdAt),
        ) { quote ->
            this[QuoteTable.ticker] = quote.ticker
            this[QuoteTable.date] = quote.date
            this[QuoteTable.close] = quote.close
            this[QuoteTable.sourceType] = quote.source
            this[QuoteTable.createdAt] = LocalDateTime.now(clock)
        }

        quotes.size
    }

    override suspend fun fetchAllBy(ticker: String): List<Quote> = transaction {
        QuoteEntity.find { QuoteTable.ticker eq ticker }
            .orderBy(QuoteTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }
}
