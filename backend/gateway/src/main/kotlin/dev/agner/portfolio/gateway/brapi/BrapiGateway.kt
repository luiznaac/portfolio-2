package dev.agner.portfolio.gateway.brapi

import com.fasterxml.jackson.annotation.JsonProperty
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.quotes.gateway.ICurrentQuoteSource
import dev.agner.portfolio.usecase.quotes.model.TheirCurrentQuote
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import kotlinx.datetime.LocalDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

// brapi.dev current quotes. Free tier: Bearer token, one ticker per call, ~30 min delay. With no
// token configured the gateway is a no-op (null), so the app still boots and the caller falls back
// to the last stored COTAHIST close.
@Service
class BrapiGateway(
    private val client: HttpClient,
    private val clock: Clock,
    @param:Value("\${gateways.brapi.host}") private val host: String,
    @param:Value("\${gateways.brapi.token:}") private val token: String,
) : ICurrentQuoteSource {

    override suspend fun fetchCurrentQuote(ticker: String): TheirCurrentQuote? {
        val response = if (token.isBlank()) null else fetch(ticker)

        return response
            ?.takeIf { it.status == HttpStatusCode.OK }
            ?.body<BrapiQuoteResponse>()
            ?.results
            ?.firstOrNull()
            ?.regularMarketPrice
            ?.let { TheirCurrentQuote(ticker = ticker, date = LocalDate.today(clock), close = it) }
    }

    private suspend fun fetch(ticker: String) = client.get(host) {
        headers.append(HttpHeaders.Authorization, "Bearer $token")
        url { path("/api/quote/$ticker") }
    }
}

private data class BrapiQuoteResponse(val results: List<BrapiQuoteResult>)

// brapi's payload is camelCase; the shared ObjectMapper is SNAKE_CASE, so the field is pinned.
private data class BrapiQuoteResult(
    @param:JsonProperty("regularMarketPrice") val regularMarketPrice: BigDecimal? = null,
)
