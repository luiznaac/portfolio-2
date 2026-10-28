package dev.agner.portfolio.usecase.quotes.repository

import dev.agner.portfolio.usecase.quotes.model.Quote

interface IQuoteRepository {

    suspend fun upsertAll(quotes: List<Quote>): Int

    suspend fun fetchAllBy(ticker: String): List<Quote>
}
