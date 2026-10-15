package dev.agner.portfolio.gateway.brapi

import dev.agner.portfolio.gateway.clientWith
import dev.agner.portfolio.usecase.quotes.model.TheirCurrentQuote
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class BrapiGatewayTest : StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.of("America/Sao_Paulo"))

    "fetches the current quote with a Bearer token" {
        val captured = mutableListOf<HttpRequestData>()
        val gateway = BrapiGateway(
            clientWith { request ->
                captured += request
                respond(
                    """{"results":[{"symbol":"PETR4","regularMarketPrice":49.11}]}""",
                    HttpStatusCode.OK,
                    jsonHeaders(),
                )
            },
            clock,
            HOST,
            TOKEN,
        )

        val quote = gateway.fetchCurrentQuote("PETR4")

        quote shouldBe TheirCurrentQuote("PETR4", LocalDate.parse("2026-10-15"), BigDecimal("49.11"))
        captured.single().headers[HttpHeaders.Authorization] shouldBe "Bearer $TOKEN"
        captured.single().url.toString() shouldBe "$HOST/api/quote/PETR4"
    }

    "returns null when no token is configured" {
        val gateway = BrapiGateway(clientWith { respond("{}", HttpStatusCode.OK, jsonHeaders()) }, clock, HOST, "")

        gateway.fetchCurrentQuote("PETR4") shouldBe null
    }

    "returns null when the provider has no result" {
        val gateway = BrapiGateway(
            clientWith { respond("""{"results":[]}""", HttpStatusCode.OK, jsonHeaders()) },
            clock,
            HOST,
            TOKEN,
        )

        gateway.fetchCurrentQuote("PETR4") shouldBe null
    }
})

private const val HOST = "https://brapi.dev"
private const val TOKEN = "dev-brapi-fixture-token"

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
