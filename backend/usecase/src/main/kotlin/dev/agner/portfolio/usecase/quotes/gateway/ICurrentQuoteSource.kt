package dev.agner.portfolio.usecase.quotes.gateway

import dev.agner.portfolio.usecase.quotes.model.TheirCurrentQuote

// A best-effort current quote from a provider (brapi.dev free tier: Bearer token, one ticker per
// call, ~30 min delay). Null means the provider had nothing for that ticker.
interface ICurrentQuoteSource {

    suspend fun fetchCurrentQuote(ticker: String): TheirCurrentQuote?
}
