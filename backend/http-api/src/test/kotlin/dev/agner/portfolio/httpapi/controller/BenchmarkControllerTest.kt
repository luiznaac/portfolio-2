package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkSeriesStore
import dev.agner.portfolio.usecase.performance.model.BenchmarkStatus
import dev.agner.portfolio.usecase.performance.model.BenchmarkStatusReport
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDateTime

class BenchmarkControllerTest : DescribeSpec({

    describe("reading the stored benchmark freshness") {

        it("serves the last successful fetch per index") {
            val store = mockk<BenchmarkSeriesStore>()
            coEvery { store.status() } returns status()

            testApplication {
                application { installRoutes(BenchmarkController(store)) }

                val response = client.get("/benchmarks")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("indexes").size() shouldBe 3
                body.get("indexes").get(0).get("index").asText() shouldBe "CDI"
                body.get("indexes").get(0).get("last_fetched_at").asText() shouldBe
                    "2026-03-01T10:30"
                (body.get("indexes").get(1).get("last_fetched_at") == null) shouldBe true
            }
        }
    }

    describe("refreshing the stored benchmark series") {

        it("re-checks every index and returns the new freshness") {
            val store = mockk<BenchmarkSeriesStore>()
            coEvery { store.refresh() } returns status()

            testApplication {
                application { installRoutes(BenchmarkController(store)) }

                val response = client.post("/benchmarks/refresh")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("indexes").size() shouldBe 3
                body.get("indexes").get(0).get("index").asText() shouldBe "CDI"
            }
        }
    }
})

private fun status() = BenchmarkStatusReport(
    indexes = listOf(
        BenchmarkStatus(BenchmarkIndex.CDI, LocalDateTime.parse("2026-03-01T10:30")),
        BenchmarkStatus(BenchmarkIndex.IPCA, null),
        BenchmarkStatus(BenchmarkIndex.IBOV, LocalDateTime.parse("2026-03-01T10:30")),
    ),
)
