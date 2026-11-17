package dev.agner.portfolio.gateway.corporateaction

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.agner.portfolio.usecase.corporateaction.BrapiFetchException
import dev.agner.portfolio.usecase.corporateaction.BrapiSyncException
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiFetch
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiIncome
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiRename
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiStockAction
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiStockEvents
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiWindow
import dev.agner.portfolio.usecase.corporateaction.gateway.IBrapiCorporateActionSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal

// brapi.dev's corporate-action and income feed. The renames endpoint is free; the stock and FII
// dividends endpoints need the Pro plan, so the sync treats an absent token or a 401/403 as a loud
// failure and only a per-batch provider failure as recoverable. Every call returns the raw body it
// parsed, which the sync persists with its audit so a later parser change needs no paid month.
@Service
class BrapiCorporateActionGateway(
    private val client: HttpClient,
    @param:Value("\${gateways.brapi.host}") private val host: String,
    @param:Value("\${gateways.brapi.token:}") private val token: String,
) : IBrapiCorporateActionSource {

    // The provider's payload is camelCase; the app's shared mapper is SNAKE_CASE, so brapi gets its
    // own reader rather than annotations on every field.
    private val mapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override suspend fun fetchRenames(tickers: List<String>): BrapiFetch<List<BrapiRename>> {
        val body = fetch("api/v2/tickers/renames", tickers)
        val response = parse(body, RenamesResponse::class.java, RENAMES_PATH)
        return BrapiFetch(
            payload = body,
            items = response.results.orEmpty().mapNotNull { it.toRename() },
        )
    }

    override suspend fun fetchStockEvents(
        tickers: List<String>,
        window: BrapiWindow,
    ): BrapiFetch<BrapiStockEvents> {
        val body = fetch("api/v2/stocks/dividends", tickers, window, includeRaw = true)
        val response = parse(body, StockDividendsResponse::class.java, STOCK_PATH)
        val results = response.results.orEmpty()
        return BrapiFetch(
            payload = body,
            items = BrapiStockEvents(
                cashDividends = results.flatMap { result -> result.cashDividends() },
                stockDividends = results.flatMap { result -> result.stockDividends() },
            ),
        )
    }

    override suspend fun fetchFundEvents(
        tickers: List<String>,
        window: BrapiWindow,
    ): BrapiFetch<List<BrapiIncome>> {
        val body = fetch("api/v2/fii/dividends", tickers, window)
        val response = parse(body, FundDividendsResponse::class.java, FUND_PATH)
        return BrapiFetch(
            payload = body,
            items = response.dividends.orEmpty().mapNotNull { it.toIncome() },
        )
    }

    private suspend fun fetch(
        path: String,
        tickers: List<String>,
        window: BrapiWindow? = null,
        includeRaw: Boolean = false,
    ): String {
        if (token.isBlank()) {
            throw BrapiSyncException("BRAPI_TOKEN is not configured; the paid sync needs a brapi token")
        }
        val response = try {
            client.get(host) {
                url {
                    appendPathSegments(*path.split('/').toTypedArray())
                    parameter("symbols", tickers.joinToString(","))
                    window?.let {
                        parameter("startDate", it.from.toString())
                        parameter("endDate", it.to.toString())
                    }
                    if (includeRaw) parameter("includeRaw", "true")
                }
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BrapiFetchException("brapi request to $path failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
        response.requireAuthorized()
        if (!response.status.isSuccess()) {
            throw BrapiFetchException("brapi returned ${response.status.value} for $path")
        }
        return response.bodyAsText()
    }

    // A body the provider changed shape on is one batch's failure, not the run's: the sync records
    // the batch's tickers and keeps the other batches.
    private fun <T> parse(body: String, type: Class<T>, path: String): T = try {
        mapper.readValue(body, type)
    } catch (e: Exception) {
        throw BrapiFetchException("brapi returned an unreadable $path payload", e)
    }

    private fun HttpResponse.requireAuthorized() {
        if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
            throw BrapiSyncException(
                detail = "brapi rejected the token (${status.value}); the sync needs a token with the Pro plan",
                userMessage = "brapi rejected the configured token",
            )
        }
    }

    private companion object {
        const val RENAMES_PATH = "tickers/renames"
        const val STOCK_PATH = "stocks/dividends"
        const val FUND_PATH = "fii/dividends"
    }
}

private data class RenamesResponse(val results: List<RenamePayload>? = null)

private data class RenamePayload(
    val oldSymbol: String? = null,
    val newSymbol: String? = null,
    val canonicalSymbol: String? = null,
    val effectiveDate: String? = null,
    val conversionRatio: BigDecimal? = null,
) {
    fun toRename(): BrapiRename? {
        val old = oldSymbol ?: return null
        val canonical = canonicalSymbol ?: newSymbol ?: return null
        val effective = effectiveDate.toLocalDate() ?: return null
        return BrapiRename(
            oldSymbol = old,
            newSymbol = newSymbol ?: canonical,
            canonicalSymbol = canonical,
            effectiveDate = effective,
            conversionRatio = conversionRatio,
        )
    }
}

private data class StockDividendsResponse(val results: List<StockDividendsResult>? = null)

private data class StockDividendsResult(
    val requestedSymbol: String? = null,
    val symbol: String? = null,
    val data: StockDividendsData? = null,
)

private data class StockDividendsData(
    val cashDividends: List<IncomePayload>? = null,
    val stockDividends: List<StockActionPayload>? = null,
)

// A result without a symbol (neither the requested nor the resolved one) has nothing to key its
// events on; the batch's other results still map.
private fun StockDividendsResult.cashDividends(): List<BrapiIncome> {
    val ticker = symbol ?: requestedSymbol ?: return emptyList()
    return data?.cashDividends.orEmpty().mapNotNull { it.toIncome(ticker) }
}

private fun StockDividendsResult.stockDividends(): List<BrapiStockAction> {
    val ticker = symbol ?: requestedSymbol ?: return emptyList()
    return data?.stockDividends.orEmpty().mapNotNull { it.toAction(ticker) }
}

private data class FundDividendsResponse(val dividends: List<IncomePayload>? = null)

private data class IncomePayload(
    val symbol: String? = null,
    val label: String? = null,
    val rate: BigDecimal? = null,
    val rawRate: BigDecimal? = null,
    val exDate: String? = null,
    val lastDatePrior: String? = null,
    val paymentDate: String? = null,
) {
    fun toIncome(defaultSymbol: String? = null): BrapiIncome? {
        val ticker = symbol ?: defaultSymbol ?: return null
        val type = label ?: return null
        return BrapiIncome(
            symbol = ticker,
            label = type,
            rate = rate,
            rawRate = rawRate,
            exDate = exDate.toLocalDate(),
            lastDatePrior = lastDatePrior.toLocalDate(),
            paymentDate = paymentDate.toLocalDate(),
        )
    }
}

private data class StockActionPayload(
    val label: String? = null,
    val factor: BigDecimal? = null,
    val exDate: String? = null,
    val approvedOn: String? = null,
) {
    fun toAction(symbol: String): BrapiStockAction? {
        val type = label ?: return null
        return BrapiStockAction(
            symbol = symbol,
            label = type,
            factor = factor,
            exDate = exDate.toLocalDate(),
            approvedOn = approvedOn.toLocalDate(),
        )
    }
}

// brapi serves a plain date on the renames endpoint and an ISO timestamp on the dividends ones;
// both carry the trading day in their first ten characters.
private fun String?.toLocalDate(): LocalDate? = this
    ?.take(10)
    ?.takeIf { it.length == 10 }
    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
