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

class BcbSgsGatewayTest : StringSpec({

    "reads the CDI series 12 for the requested range" {
        val requested = mutableListOf<String>()
        val gateway = BcbSgsGateway(
            clientWith { request ->
                requested += request.url.toString()
                respond(
                    """[{"data":"02/01/2026","valor":"0.05"},{"data":"03/01/2026","valor":"0.05"}]""",
                    HttpStatusCode.OK,
                    jsonHeaders(),
                )
            },
            HOST,
        )

        val series = gateway.fetchSeries(
            BenchmarkIndex.CDI,
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-31"),
        )

        requested.single().startsWith("$HOST/dados/serie/bcdata.sgs.12/dados") shouldBe true
        requested.single().contains("dataInicial=01%2F01%2F2026") shouldBe true
        requested.single().contains("dataFinal=31%2F01%2F2026") shouldBe true
        series.map { it.date } shouldBe listOf(LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-03"))
        series.first().value.compareTo(BigDecimal("0.05")) shouldBe 0
    }

    "is the SGS source for CDI and IPCA only" {
        val gateway = BcbSgsGateway(clientWith { respond("[]", HttpStatusCode.OK, jsonHeaders()) }, HOST)

        gateway.source shouldBe BenchmarkSource.SGS
        gateway.indexes shouldBe setOf(BenchmarkIndex.CDI, BenchmarkIndex.IPCA)
    }

    "reads the IPCA series 433" {
        val requested = mutableListOf<String>()
        val gateway = BcbSgsGateway(
            clientWith { request ->
                requested += request.url.encodedPath
                respond("[]", HttpStatusCode.OK, jsonHeaders())
            },
            HOST,
        )

        gateway.fetchSeries(BenchmarkIndex.IPCA, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))

        requested shouldBe listOf("/dados/serie/bcdata.sgs.433/dados")
    }

    "an absent series is an empty list, not a failure" {
        val gateway = BcbSgsGateway(clientWith { respondError(HttpStatusCode.NotFound) }, HOST)

        gateway.fetchSeries(BenchmarkIndex.IPCA, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
            .shouldBe(emptyList())
    }
})

private const val HOST = "https://api.bcb.gov.br"
