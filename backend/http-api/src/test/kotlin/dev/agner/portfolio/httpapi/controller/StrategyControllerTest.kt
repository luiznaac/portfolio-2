package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.import.strategy.StrategyReportParserResolver
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.DiffChange
import dev.agner.portfolio.usecase.strategy.model.DiffWeight
import dev.agner.portfolio.usecase.strategy.model.EditionDetail
import dev.agner.portfolio.usecase.strategy.model.EditionDiff
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.EditionImport
import dev.agner.portfolio.usecase.strategy.model.EditionStatus
import dev.agner.portfolio.usecase.strategy.model.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.model.Rating
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class StrategyControllerTest : DescribeSpec({

    describe("listing strategies") {

        it("serves the catalog with snake_case fields") {
            val service = mockk<StrategyService>()
            coEvery { service.fetchAll() } returns listOf(strategy)

            testApplication {
                application { installRoutes(StrategyController(service, resolver())) }

                val response = client.get("/strategies")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText()).get(0)
                body.get("name").asText() shouldBe "Top Dividendos"
                body.get("asset_class").asText() shouldBe "ACOES"
                body.get("weight").decimalValue().compareTo(BigDecimal("34.00")) shouldBe 0
            }
        }
    }

    describe("creating a strategy") {

        it("creates from the name, class and weight") {
            val service = mockk<StrategyService>()
            coEvery { service.create(any()) } returns strategy

            testApplication {
                application { installRoutes(StrategyController(service, resolver())) }

                val response = client.post("/strategies") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Top Dividendos","asset_class":"ACOES","weight":34.00}""")
                }

                response.status shouldBe HttpStatusCode.Created
                coVerify(exactly = 1) {
                    service.create(
                        match { it == StrategyCreation("Top Dividendos", AssetClass.ACOES, BigDecimal("34.00")) },
                    )
                }
            }
        }
    }

    describe("reading a strategy's editions") {

        it("serves the editions with their entries and stored diff") {
            val service = mockk<StrategyService>()
            coEvery { service.fetchEditions(1) } returns listOf(edition)

            testApplication {
                application { installRoutes(StrategyController(service, resolver())) }

                val response = client.get("/strategies/1/editions")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText()).get(0)
                body.get("reference_month").asText() shouldBe "2026-09"
                body.get("status").asText() shouldBe "STAGED"
                body.get("entries").get(0).get("ticker").asText() shouldBe "PETR4"
                body.get("entries").get(0).get("do_not_trade").asBoolean() shouldBe false
                body.get("diff_from_previous").get("entered").get(0).get("ticker").asText() shouldBe "CXSE3"
                body.get("diff_from_previous").get("exited").get(0).get("weight").decimalValue()
                    .compareTo(BigDecimal("0.00")) shouldBe 0
            }
        }

        it("404s when the strategy does not exist") {
            val service = mockk<StrategyService>()
            coEvery { service.fetchEditions(404) } returns null

            testApplication {
                application { installRoutes(StrategyController(service, resolver())) }

                client.get("/strategies/404/editions").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("importing a report") {

        it("parses the upload and stages the edition") {
            val service = mockk<StrategyService>()
            val parser = mockk<StrategyReportParserResolver>()
            every { parser.resolve("relatorio.pdf", any()) } returns parsedReport
            coEvery { service.importEdition(1, parsedReport) } returns EditionImport("XpEquityCarteira", edition)

            testApplication {
                application { installRoutes(StrategyController(service, parser)) }

                val response = client.post("/strategies/1/editions/import") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "file",
                                    "pdf-bytes".toByteArray(),
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"relatorio.pdf\"")
                                    },
                                )
                            },
                        ),
                    )
                }

                response.status shouldBe HttpStatusCode.Created
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("parser").asText() shouldBe "XpEquityCarteira"
                body.get("edition").get("reference_month").asText() shouldBe "2026-09"
                coVerify(exactly = 1) { service.importEdition(1, parsedReport) }
            }
        }

        it("404s when the strategy does not exist") {
            val service = mockk<StrategyService>()
            val parser = mockk<StrategyReportParserResolver>()
            every { parser.resolve(any(), any()) } returns parsedReport
            coEvery { service.importEdition(404, parsedReport) } returns null

            testApplication {
                application { installRoutes(StrategyController(service, parser)) }

                val response = client.post("/strategies/404/editions/import") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "file",
                                    "pdf-bytes".toByteArray(),
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"relatorio.pdf\"")
                                    },
                                )
                            },
                        ),
                    )
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("activating an edition") {

        it("applies the reviewed entries and activates") {
            val service = mockk<StrategyService>()
            coEvery { service.activateEdition(1, YearMonth.parse("2026-09"), any()) } returns edition

            testApplication {
                application { installRoutes(StrategyController(service, resolver())) }

                val response = client.post("/strategies/1/editions") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "reference_month": "2026-09",
                          "entries": [
                            {"ticker":"PETR4","weight":12.5,"rating":"BUY","target_price":63.00,"do_not_trade":false}
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) {
                    service.activateEdition(1, YearMonth.parse("2026-09"), listOf(entry))
                }
            }
        }

        it("404s when no edition is staged for the month") {
            val service = mockk<StrategyService>()
            coEvery { service.activateEdition(1, any(), any()) } returns null

            testApplication {
                application { installRoutes(StrategyController(service, resolver())) }

                val response = client.post("/strategies/1/editions") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"reference_month":"2026-09","entries":[]}""")
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})

private fun resolver() = StrategyReportParserResolver(emptyList())

private val strategy = Strategy(1, "Top Dividendos", AssetClass.ACOES, BigDecimal("34.00"))

private val entry = EditionEntry(
    ticker = "PETR4",
    weight = BigDecimal("12.5"),
    rating = Rating.BUY,
    targetPrice = BigDecimal("63.00"),
)

private val edition = EditionDetail(
    id = 9,
    strategyId = 1,
    referenceMonth = YearMonth.parse("2026-09"),
    status = EditionStatus.STAGED,
    diffFromPrevious = EditionDiff(
        entered = listOf(DiffWeight("CXSE3", BigDecimal("5.00"))),
        exited = listOf(DiffWeight("B3SA3", BigDecimal("0.00"))),
        changed = listOf(
            DiffChange(
                ticker = "PETR4",
                weightBefore = BigDecimal("10.00"),
                weightAfter = BigDecimal("12.50"),
                ratingBefore = Rating.BUY,
                ratingAfter = Rating.BUY,
            ),
        ),
    ),
    entries = listOf(entry),
)

private val parsedReport = ParsedStrategyReport(
    referenceMonth = YearMonth.parse("2026-09"),
    parser = "XpEquityCarteira",
    entries = listOf(entry),
)
