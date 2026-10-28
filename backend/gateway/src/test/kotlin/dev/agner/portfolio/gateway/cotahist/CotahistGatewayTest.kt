package dev.agner.portfolio.gateway.cotahist

import dev.agner.portfolio.gateway.clientWith
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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

    "downloads the dated file and returns its parsed rows" {
        val requested = mutableListOf<String>()
        val gateway = CotahistGateway(
            clientWith { request ->
                requested += request.url.toString()
                respond(zipOf(petr4Record()), HttpStatusCode.OK, zipHeaders())
            },
            parser,
            clock,
            HOST,
        )

        val rows = gateway.fetchTradingDay()

        requested.first() shouldBe "$HOST/InstDados/SerHist/COTAHIST_D14102026.ZIP"
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

    "returns empty when the whole lookback window is unpublished" {
        val gateway = CotahistGateway(clientWith { respondError(HttpStatusCode.NotFound) }, parser, clock, HOST)

        gateway.fetchTradingDay() shouldBe emptyList()
    }
})

private const val HOST = "https://bvmf.bmfbovespa.com.br"

private fun zipHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())

private fun zipOf(vararg lines: String): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("COTAHIST_D.TXT"))
        lines.forEach { zip.write((it + "\n").toByteArray()) }
        zip.closeEntry()
    }
    return out.toByteArray()
}
