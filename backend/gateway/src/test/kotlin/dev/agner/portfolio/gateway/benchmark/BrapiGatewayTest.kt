package dev.agner.portfolio.gateway.benchmark

import dev.agner.portfolio.gateway.clientWith
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkSource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class BrapiGatewayTest : StringSpec({

    "requests the BVSP symbol and reads its historical closes" {
        val requested = mutableListOf<String>()
        val gateway = BrapiGateway(
            clientWith { request ->
                requested += request.url.toString()
                respond(BODY, HttpStatusCode.OK, jsonHeaders())
            },
            HOST,
            "secret",
        )

        val series = gateway.fetchSeries(
            BenchmarkIndex.IBOV,
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
        )

        requested.single().startsWith("$HOST/api/quote/%5EBVSP") shouldBe true
        requested.single().contains("range=max") shouldBe true
        requested.single().contains("interval=1d") shouldBe true
        requested.single().contains("token=secret") shouldBe true
        series.map { it.date } shouldBe listOf(LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-30"))
        series.last().value.compareTo(BigDecimal("121000")) shouldBe 0
    }

    "is the first IBOV source and omits an absent token" {
        val requested = mutableListOf<String>()
        val gateway = BrapiGateway(
            clientWith { request ->
                requested += request.url.toString()
                respond(BODY, HttpStatusCode.OK, jsonHeaders())
            },
            HOST,
            "",
        )

        gateway.fetchSeries(BenchmarkIndex.IBOV, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))

        gateway.source shouldBe BenchmarkSource.BRAPI
        gateway.indexes shouldBe setOf(BenchmarkIndex.IBOV)
        gateway.order shouldBe 10
        requested.single().contains("range=3mo") shouldBe true
        requested.single().contains("token=") shouldBe false
    }

    "an unavailable quote is an empty list so the chain can fall through" {
        val gateway = BrapiGateway(clientWith { respondError(HttpStatusCode.TooManyRequests) }, HOST, "")

        gateway.fetchSeries(BenchmarkIndex.IBOV, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
            .shouldBe(emptyList())
    }
})

private const val HOST = "https://brapi.dev"

private const val BODY = """
{"results":[{"symbol":"^BVSP","historicalDataPrice":[
  {"date":1767312000,"close":120000.0},
  {"date":1769731200,"close":121000.0},
  {"date":1772150400,"close":null}
]}]}
"""
