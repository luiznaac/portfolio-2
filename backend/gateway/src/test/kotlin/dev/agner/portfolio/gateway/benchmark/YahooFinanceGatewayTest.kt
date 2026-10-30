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

class YahooFinanceGatewayTest : StringSpec({

    "requests the BVSP chart and pairs timestamps with closes" {
        val requested = mutableListOf<String>()
        val gateway = YahooFinanceGateway(
            clientWith { request ->
                requested += request.url.toString()
                respond(BODY, HttpStatusCode.OK, jsonHeaders())
            },
            HOST,
        )

        val series = gateway.fetchSeries(
            BenchmarkIndex.IBOV,
            LocalDate.parse("2025-12-22"),
            LocalDate.parse("2026-01-31"),
        )

        requested.single().startsWith("$HOST/v8/finance/chart/%5EBVSP") shouldBe true
        requested.single().contains("interval=1d") shouldBe true
        requested.single().contains("period1=1766361600") shouldBe true
        requested.single().contains("period2=1769904000") shouldBe true
        series.map { it.date } shouldBe listOf(
            LocalDate.parse("2026-01-02"),
            LocalDate.parse("2026-01-30"),
        )
        series.last().value.compareTo(BigDecimal("121000")) shouldBe 0
    }

    "is the fallback IBOV source" {
        val gateway = YahooFinanceGateway(clientWith { respond(BODY, HttpStatusCode.OK, jsonHeaders()) }, HOST)

        gateway.source shouldBe BenchmarkSource.YAHOO
        gateway.indexes shouldBe setOf(BenchmarkIndex.IBOV)
        gateway.order shouldBe 20
    }

    "an unavailable chart is an empty list" {
        val gateway = YahooFinanceGateway(clientWith { respondError(HttpStatusCode.NotFound) }, HOST)

        gateway.fetchSeries(BenchmarkIndex.IBOV, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
            .shouldBe(emptyList())
    }

    "a chart with no result is an empty list" {
        val gateway = YahooFinanceGateway(
            clientWith {
                respond(
                    """{"chart":{"result":null,"error":{"code":"Not Found"}}}""",
                    HttpStatusCode.OK,
                    jsonHeaders(),
                )
            },
            HOST,
        )

        gateway.fetchSeries(BenchmarkIndex.IBOV, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
            .shouldBe(emptyList())
    }
})

private const val HOST = "https://query1.finance.yahoo.com"

private const val BODY = """
{"chart":{"result":[{"timestamp":[1767312000,1769731200,1772150400],
"indicators":{"quote":[{"close":[120000.0,121000.0,null]}]}}],"error":null}}
"""
