package dev.agner.portfolio.gateway.cotahist

import dev.agner.portfolio.gateway.clientWith
import dev.agner.portfolio.usecase.catalog.gateway.CotahistFetchException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CotahistGatewayTest : StringSpec({
    val parser = CotahistParser()
    // "Yesterday" (BRT) of this instant is 2026-10-14.
    val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.of("America/Sao_Paulo"))

    "downloads the dated file with an explicit Accept header and returns its parsed rows" {
        val requested = mutableListOf<String>()
        val accepts = mutableListOf<String?>()
        val gateway = CotahistGateway(
            clientWith { request ->
                requested += request.url.toString()
                accepts += request.headers[HttpHeaders.Accept]
                respond(zipOf(petr4Record()), HttpStatusCode.OK, zipHeaders())
            },
            parser,
            clock,
            HOST,
        )

        val rows = gateway.fetchTradingDay()

        requested.first() shouldBe "$HOST/InstDados/SerHist/COTAHIST_D14102026.ZIP"
        accepts.first() shouldBe "*/*"
        rows.map { it.ticker } shouldBe listOf("PETR4")
    }

    "walks back over unpublished days" {
        val gateway = CotahistGateway(
            clientWith { request ->
                if (request.url.encodedPath.endsWith("COTAHIST_D14102026.ZIP")) {
                    respondError(HttpStatusCode.NotFound)
                } else {
                    respond(zipOf(petr4Record()), HttpStatusCode.OK, zipHeaders())
                }
            },
            parser,
            clock,
            HOST,
        )

        gateway.fetchTradingDay().map { it.ticker } shouldBe listOf("PETR4")
    }

    "surfaces a server error instead of walking the lookback or returning empty" {
        val gateway = CotahistGateway(
            clientWith { respondError(HttpStatusCode.InternalServerError) },
            parser,
            clock,
            HOST,
        )

        shouldThrow<CotahistFetchException> { gateway.fetchTradingDay() }
    }

    "surfaces a parse failure instead of collapsing to empty" {
        val gateway = CotahistGateway(
            clientWith { respond("not a zip".toByteArray(), HttpStatusCode.OK, zipHeaders()) },
            parser,
            clock,
            HOST,
        )

        shouldThrow<CotahistFetchException> { gateway.fetchTradingDay() }
    }

    "surfaces a lookback window with no published file instead of returning a silent zero" {
        val gateway = CotahistGateway(clientWith { respondError(HttpStatusCode.NotFound) }, parser, clock, HOST)

        shouldThrow<CotahistFetchException> { gateway.fetchTradingDay() }
    }

    "surfaces a daily file with no quote records instead of returning a silent zero" {
        val gateway = CotahistGateway(
            clientWith { respond(zipOf(cotahistLine(1 to "99", 3 to "20261014")), HttpStatusCode.OK, zipHeaders()) },
            parser,
            clock,
            HOST,
        )

        shouldThrow<CotahistFetchException> { gateway.fetchTradingDay() }
    }

    "fetches an annual file through its own URL and streams its parsed rows" {
        val requested = mutableListOf<String>()
        val accepts = mutableListOf<String?>()
        val gateway = CotahistGateway(
            clientWith { request ->
                requested += request.url.toString()
                accepts += request.headers[HttpHeaders.Accept]
                respond(
                    zipOfNamed(
                        "COTAHIST_A2020.TXT",
                        annualRecord(ticker = "PETR4", date = "20200102", close = "0000000029500"),
                        annualRecord(ticker = "PETR4", date = "20201230", close = "0000000021500"),
                    ),
                    HttpStatusCode.OK,
                    zipHeaders(),
                )
            },
            parser,
            clock,
            HOST,
        )

        val rows = gateway.fetchYear(2020).toList()

        requested.single() shouldBe "$HOST/InstDados/SerHist/COTAHIST_A2020.ZIP"
        accepts.single() shouldBe "*/*"
        rows.map { it.ticker to it.closePrice } shouldBe listOf(
            "PETR4" to java.math.BigDecimal("295.00"),
            "PETR4" to java.math.BigDecimal("215.00"),
        )
    }

    "surfaces an annual download failure" {
        val gateway = CotahistGateway(
            clientWith { respondError(HttpStatusCode.NotFound) },
            parser,
            clock,
            HOST,
        )

        shouldThrow<CotahistFetchException> { gateway.fetchYear(2020).toList() }
    }

    "surfaces an annual file with no quote records" {
        val gateway = CotahistGateway(
            clientWith {
                respond(zipOf(cotahistLine(1 to "99", 3 to "20201230")), HttpStatusCode.OK, zipHeaders())
            },
            parser,
            clock,
            HOST,
        )

        shouldThrow<CotahistFetchException> { gateway.fetchYear(2020).toList() }
    }
})

private const val HOST = "https://bvmf.bmfbovespa.com.br"

private fun zipHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())

private fun zipOf(vararg lines: String) = zipOfNamed("COTAHIST_D.TXT", *lines)

private fun zipOfNamed(entryName: String, vararg lines: String): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry(entryName))
        lines.forEach { zip.write((it + "\n").toByteArray()) }
        zip.closeEntry()
    }
    return out.toByteArray()
}

private fun annualRecord(ticker: String, date: String, close: String) = cotahistLine(
    1 to "01",
    3 to date,
    11 to "02",
    13 to ticker,
    25 to "010",
    28 to "PETROBRAS",
    109 to close,
    231 to "BRPETRACNPR6",
)
