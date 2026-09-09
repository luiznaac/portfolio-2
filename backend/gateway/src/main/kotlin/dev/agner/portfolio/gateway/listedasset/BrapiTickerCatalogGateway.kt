package dev.agner.portfolio.gateway.listedasset

import com.fasterxml.jackson.annotation.JsonProperty
import dev.agner.portfolio.usecase.listedasset.catalog.gateway.ITickerCatalogGateway
import dev.agner.portfolio.usecase.listedasset.catalog.model.TickerCatalogEntry
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * B3's whole universe of stocks/FIIs/ETFs/BDRs, from brapi's `/api/v2/tickers` — free, no token
 * required (confirmed against the live endpoint). One page per subtype comfortably fits under the
 * 2000-item page-size cap (~785 stocks, ~335 FIIs, ~185 ETFs, ~880 BDRs as of writing), so this is
 * 4 requests total, not a paginated crawl. Used only to populate the local ticker_catalog search
 * index — never called per-consolidation, that's still BrapiGateway's quote endpoint.
 */
@Service
class BrapiTickerCatalogGateway(
    private val client: HttpClient,
    @param:Value("\${gateways.brapi.host}") private val host: String,
) : ITickerCatalogGateway {

    override suspend fun fetchAll(): List<TickerCatalogEntry> =
        SUPPORTED_SUBTYPES.flatMap { (subType, kind) -> fetchSubType(subType, kind) }

    private suspend fun fetchSubType(subType: String, kind: AssetKind): List<TickerCatalogEntry> {
        val response = client.get(host) {
            url { path("/api/v2/tickers") }
            parameter("subType", subType)
            parameter("limit", PAGE_SIZE)
        }

        if (response.status != HttpStatusCode.OK) return emptyList()

        return response.body<BrapiTickersResponse>().results.map {
            TickerCatalogEntry(ticker = it.symbol, name = (it.longName ?: it.name), kind = kind)
        }
    }

    private companion object {
        const val PAGE_SIZE = 2000

        // subType -> our AssetKind. brapi also has "unit"/"fi-infra"/"fi-agro"/"fip"/"fidc" —
        // outside the plan's scope (stocks/FIIs/ETFs/BDRs only), so those are simply not fetched.
        val SUPPORTED_SUBTYPES = mapOf(
            "stock" to AssetKind.STOCK,
            "fii" to AssetKind.FII,
            "etf" to AssetKind.ETF,
            "bdr" to AssetKind.BDR,
        )
    }
}

private data class BrapiTickersResponse(
    @param:JsonProperty("results") val results: List<BrapiTickerRow> = emptyList(),
)

private data class BrapiTickerRow(
    @param:JsonProperty("symbol") val symbol: String,
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("longName") val longName: String?,
)
