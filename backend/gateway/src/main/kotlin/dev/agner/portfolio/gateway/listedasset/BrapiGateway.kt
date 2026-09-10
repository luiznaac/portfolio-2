package dev.agner.portfolio.gateway.listedasset

import com.fasterxml.jackson.annotation.JsonProperty
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.model.Quote
import dev.agner.portfolio.usecase.listedasset.model.QuoteSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import kotlinx.datetime.toKotlinLocalDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * Live quote from brapi.dev (free tier, token-gated). There is no fallback to B3's COTAHIST daily
 * file yet: a missing quote fails consolidation for that asset rather than silently pricing off a
 * stale value.
 */
@Service
class BrapiGateway(
    private val client: HttpClient,
    @param:Value("\${gateways.brapi.host}") private val host: String,
    @param:Value("\${gateways.brapi.token}") private val token: String,
) : IQuoteGateway {

    override suspend fun getQuote(asset: ListedAsset): Quote? {
        val response = client.get(host) {
            url { path("/api/quote/${asset.ticker}") }
            parameter("token", token)
        }

        if (response.status != HttpStatusCode.OK) return null

        val result = response.body<BrapiQuoteResponse>().results.firstOrNull() ?: return null

        return Quote(
            price = result.regularMarketPrice,
            date = Instant.parse(result.regularMarketTime).atZone(ZoneOffset.UTC).toLocalDate().toKotlinLocalDate(),
            source = QuoteSource.BRAPI,
        )
    }
}

// Fields explicitly @JsonProperty-annotated because the shared Jackson ObjectMapper (JsonMapper.kt)
// is configured SNAKE_CASE for our own API — an explicit name always overrides that strategy, so
// these still match brapi's actual camelCase response regardless.
private data class BrapiQuoteResponse(
    @param:JsonProperty("results") val results: List<BrapiQuote> = emptyList(),
)

private data class BrapiQuote(
    @param:JsonProperty("regularMarketPrice") val regularMarketPrice: BigDecimal,
    @param:JsonProperty("regularMarketTime") val regularMarketTime: String,
)
