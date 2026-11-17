package dev.agner.portfolio.gateway.corporateaction

import dev.agner.portfolio.gateway.benchmark.jsonHeaders
import dev.agner.portfolio.gateway.clientWith
import dev.agner.portfolio.usecase.corporateaction.BrapiFetchException
import dev.agner.portfolio.usecase.corporateaction.BrapiSyncException
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiWindow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import java.io.File
import java.math.BigDecimal

class BrapiCorporateActionGatewayTest : StringSpec({

    "reads the free renames endpoint with a bearer token and parses its result" {
        val requests = mutableListOf<HttpRequestData>()
        val body = fixture("brapi/ticker-renames.json")
        val gateway = gateway { request ->
            requests += request
            respond(body, HttpStatusCode.OK, jsonHeaders())
        }

        val fetch = gateway.fetchRenames(listOf("BIDI11", "INBR32"))

        val request = requests.single()
        request.url.encodedPath shouldBe "/api/v2/tickers/renames"
        request.url.parameters["symbols"] shouldBe "BIDI11,INBR32"
        request.headers[HttpHeaders.Authorization] shouldBe "Bearer $TOKEN"
        fetch.payload shouldBe body
        fetch.items.map { it.canonicalSymbol } shouldBe listOf("INBR32", "INBR32")
        fetch.items.first().effectiveDate shouldBe LocalDate.parse("2024-01-29")
        fetch.items.last().conversionRatio?.compareTo(BigDecimal("1.1")) shouldBe 0
    }

    "reads the stock dividends endpoint over the window and parses both event families" {
        val requests = mutableListOf<HttpRequestData>()
        val body = fixture("brapi/stock-dividends.json")
        val gateway = gateway { request ->
            requests += request
            respond(body, HttpStatusCode.OK, jsonHeaders())
        }

        val fetch = gateway.fetchStockEvents(listOf("PETR4"), WINDOW)

        val request = requests.single()
        request.url.encodedPath shouldBe "/api/v2/stocks/dividends"
        request.url.parameters["symbols"] shouldBe "PETR4"
        request.url.parameters["startDate"] shouldBe "2018-01-01"
        request.url.parameters["endDate"] shouldBe "2026-11-17"
        request.url.parameters["includeRaw"] shouldBe "true"
        request.headers[HttpHeaders.Authorization] shouldBe "Bearer $TOKEN"
        fetch.items.cashDividends.map { it.label } shouldBe listOf("JCP", "DIVIDENDO")
        fetch.items.cashDividends.first().paymentDate shouldBe LocalDate.parse("2026-08-20")
        fetch.items.cashDividends.last().rawRate shouldBe null
        fetch.items.stockDividends.map { it.label } shouldBe
            listOf("DESDOBRAMENTO", "GRUPAMENTO", "BONIFICACAO")
        fetch.items.stockDividends.first().exDate shouldBe null
        fetch.items.stockDividends[1].factor?.compareTo(BigDecimal("0.01")) shouldBe 0
    }

    "reads the FII dividends endpoint and parses a rendimento and an amortization" {
        val requests = mutableListOf<HttpRequestData>()
        val body = fixture("brapi/fii-dividends.json")
        val gateway = gateway { request ->
            requests += request
            respond(body, HttpStatusCode.OK, jsonHeaders())
        }

        val fetch = gateway.fetchFundEvents(listOf("MXRF11"), WINDOW)

        val request = requests.single()
        request.url.encodedPath shouldBe "/api/v2/fii/dividends"
        request.url.parameters["symbols"] shouldBe "MXRF11"
        request.url.parameters["startDate"] shouldBe "2018-01-01"
        fetch.items.map { it.label } shouldBe listOf("RENDIMENTO", "AMORTIZAÇÃO")
        fetch.items.first().exDate shouldBe null
        fetch.items.first().lastDatePrior shouldBe LocalDate.parse("2026-10-30")
        fetch.items.last().rawRate?.compareTo(BigDecimal("0.5")) shouldBe 0
    }

    "a missing token fails the sync loudly without calling brapi" {
        val requests = mutableListOf<HttpRequestData>()
        val gateway = gateway(token = "") { request ->
            requests += request
            respond("{}", HttpStatusCode.OK, jsonHeaders())
        }

        shouldThrow<BrapiSyncException> { gateway.fetchRenames(listOf("PETR4")) }

        requests.shouldBeEmpty()
    }

    "a rejected token (403) fails the sync loudly" {
        val gateway = gateway { respondError(HttpStatusCode.Forbidden) }

        shouldThrow<BrapiSyncException> { gateway.fetchStockEvents(listOf("PETR4"), WINDOW) }
    }

    "a provider failure on one batch is a fetch failure the sync can report" {
        val gateway = gateway { respondError(HttpStatusCode.InternalServerError) }

        shouldThrow<BrapiFetchException> { gateway.fetchStockEvents(listOf("PETR4"), WINDOW) }
    }

    "an unreadable payload is a batch failure, not a run abort" {
        val gateway = gateway { respond("not json", HttpStatusCode.OK, jsonHeaders()) }

        shouldThrow<BrapiFetchException> { gateway.fetchStockEvents(listOf("PETR4"), WINDOW) }
    }

    "a rate-limited call is a fetch failure, not a credential failure" {
        val gateway = gateway { respondError(HttpStatusCode.TooManyRequests) }

        shouldThrow<BrapiFetchException> { gateway.fetchFundEvents(listOf("MXRF11"), WINDOW) }
    }
})

private const val HOST = "https://brapi.dev"
private const val TOKEN = "secret"
private val WINDOW = BrapiWindow(LocalDate.parse("2018-01-01"), LocalDate.parse("2026-11-17"))

private fun gateway(
    token: String = TOKEN,
    handler: MockRequestHandler,
) = BrapiCorporateActionGateway(client = clientWith(handler), host = HOST, token = token)

private fun fixture(path: String): String =
    File(
        System.getProperty("contractsDir")
            ?: error("contractsDir system property is not set; see backend/build.gradle.kts"),
    ).resolve(path).readText()
