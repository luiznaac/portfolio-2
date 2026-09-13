package dev.agner.portfolio.gateway.listedasset

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Live quote from brapi.dev (free tier, token-gated). There is no fallback to B3's COTAHIST daily
 * file yet: a missing quote fails consolidation for that asset rather than silently pricing off a
 * stale value.
 *
 * That "never price off a stale value" rule has one deliberate exception: [getQuote] memoizes a
 * fetched quote for [QUOTE_CACHE_TTL], so a request chaining two quote consumers (e.g.
 * `OrderPlanService` then `StepUpService`) pays one brapi call per ticker instead of two against a
 * free-tier token. The window is short enough that no consumer sees a quote from a previous
 * session, and a *missing* quote is never cached, so a failure still fails instead of reviving an
 * older value.
 */
@Service
open class BrapiGateway(
    private val client: HttpClient,
    @param:Value("\${gateways.brapi.host}") private val host: String,
    @param:Value("\${gateways.brapi.token}") private val token: String,
) : IQuoteGateway {

    private val quoteCache: Cache<String, Quote> = Caffeine.newBuilder()
        .expireAfterWrite(QUOTE_CACHE_TTL)
        .build()

    override suspend fun getQuote(asset: ListedAsset): Quote? {
        quoteCache.getIfPresent(asset.ticker)?.let { return it }

        return fetchQuote(asset)?.also { quoteCache.put(asset.ticker, it) }
    }

    /** The live HTTP read, split out so a test can exercise the cache without a network. */
    protected open suspend fun fetchQuote(asset: ListedAsset): Quote? {
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

    private companion object {
        val QUOTE_CACHE_TTL: Duration = Duration.ofMinutes(1)
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
