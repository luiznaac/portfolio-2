package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.performance.PerformanceService
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkBlend
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkContribution
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.model.ClassPerformance
import dev.agner.portfolio.usecase.performance.model.MonthPerformance
import dev.agner.portfolio.usecase.performance.model.PerformanceDecomposition
import dev.agner.portfolio.usecase.performance.model.PerformanceSeries
import dev.agner.portfolio.usecase.performance.model.PeriodBenchmark
import dev.agner.portfolio.usecase.performance.model.PeriodClass
import dev.agner.portfolio.usecase.performance.model.PeriodPerformance
import dev.agner.portfolio.usecase.performance.model.TickerPerformance
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class PerformanceControllerTest : DescribeSpec({

    describe("reading a closed month's performance") {

        it("serves the decomposition, xirr and benchmark") {
            val service = mockk<PerformanceService>()
            coEvery { service.fetchForMonth(7) } returns monthPerformance()

            testApplication {
                application { installRoutes(PerformanceController(service)) }

                val response = client.get("/months/7/performance")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("period").asText() shouldBe "2026-10"
                body.get("decomposition").get("return_percentage").decimalValue()
                    .compareTo(BigDecimal("1.0000")) shouldBe 0
                body.get("xirr").decimalValue().compareTo(BigDecimal("12.0000")) shouldBe 0
                body.get("benchmark").get("return_percentage").decimalValue()
                    .compareTo(BigDecimal("2.0000")) shouldBe 0
                body.get("classes").get(0).get("asset_class").asText() shouldBe "ACOES"
                body.get("tickers").get(0).get("ticker").asText() shouldBe "PETR4"
            }
        }

        it("404s for a month that was never closed") {
            val service = mockk<PerformanceService>()
            coEvery { service.fetchForMonth(99) } returns null

            testApplication {
                application { installRoutes(PerformanceController(service)) }

                client.get("/months/99/performance").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("reading the performance series") {

        it("serves the monthly points and the since-inception xirr") {
            val service = mockk<PerformanceService>()
            coEvery { service.fetchSeries() } returns series()

            testApplication {
                application { installRoutes(PerformanceController(service)) }

                val response = client.get("/performance/series")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("months").size() shouldBe 1
                body.get("months").get(0).get("period").asText() shouldBe "2026-10"
                body.get("months").get(0).get("benchmarks").get(0).get("index").asText() shouldBe "CDI"
                body.get("months").get(0).get("benchmarks").get(0).get("return_percentage")
                    .decimalValue().compareTo(BigDecimal("0.9000")) shouldBe 0
                body.get("months").get(0).get("classes").get(0).get("asset_class").asText() shouldBe "ACOES"
                body.get("months").get(0).get("classes").get(0).get("value_end")
                    .decimalValue().compareTo(BigDecimal("101000")) shouldBe 0
                body.get("xirr").decimalValue().compareTo(BigDecimal("12.0000")) shouldBe 0
            }
        }
    }
})

private fun monthPerformance() = MonthPerformance(
    monthId = 7,
    period = YearMonth.parse("2026-10"),
    decomposition = PerformanceDecomposition(
        valueStart = BigDecimal("100000"),
        valueEnd = BigDecimal("101000"),
        netAportes = BigDecimal.ZERO,
        netIncome = BigDecimal.ZERO,
        returnPercentage = BigDecimal("1.0000"),
    ),
    xirr = BigDecimal("12.0000"),
    benchmark = BenchmarkBlend(
        returnPercentage = BigDecimal("2.0000"),
        contributions = listOf(
            BenchmarkContribution("ACOES", BigDecimal("100"), BenchmarkIndex.IBOV, BigDecimal("2.0000")),
        ),
    ),
    classes = listOf(
        ClassPerformance(
            assetClass = AssetClass.ACOES,
            decomposition = PerformanceDecomposition(
                valueStart = BigDecimal("100000"),
                valueEnd = BigDecimal("101000"),
                netAportes = BigDecimal.ZERO,
                netIncome = BigDecimal.ZERO,
                returnPercentage = BigDecimal("1.0000"),
            ),
            benchmarkReturn = BigDecimal("2.0000"),
        ),
    ),
    tickers = listOf(
        TickerPerformance(
            ticker = "PETR4",
            assetClass = AssetClass.ACOES,
            decomposition = PerformanceDecomposition(
                valueStart = BigDecimal("100000"),
                valueEnd = BigDecimal("101000"),
                netAportes = BigDecimal.ZERO,
                netIncome = BigDecimal.ZERO,
                returnPercentage = BigDecimal("1.0000"),
            ),
        ),
    ),
)

private fun series() = PerformanceSeries(
    months = listOf(
        PeriodPerformance(
            period = YearMonth.parse("2026-10"),
            decomposition = PerformanceDecomposition(
                valueStart = BigDecimal("100000"),
                valueEnd = BigDecimal("101000"),
                netAportes = BigDecimal.ZERO,
                netIncome = BigDecimal.ZERO,
                returnPercentage = BigDecimal("1.0000"),
            ),
            xirr = BigDecimal("12.0000"),
            benchmarkReturn = BigDecimal("2.0000"),
            benchmarks = listOf(PeriodBenchmark(BenchmarkIndex.CDI, BigDecimal("0.9000"))),
            classes = listOf(PeriodClass(AssetClass.ACOES, BigDecimal("101000"))),
        ),
    ),
    xirr = BigDecimal("12.0000"),
)
