package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.StrategyTargetsExceedClassException
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.SubClassTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerTarget
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class MonthControllerTest : DescribeSpec({

    describe("reading the current month") {

        it("serves the month and its targets with snake_case and an ISO period") {
            val service = mockk<MonthService>()
            coEvery { service.fetchCurrent() } returns currentMonth

            testApplication {
                application { installRoutes(MonthController(service)) }

                val response = client.get("/month/current")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("month").get("period").asText() shouldBe "2026-09"
                body.get("month").get("status").asText() shouldBe "OPEN"
                body.get("targets").get("classes").get(0).get("asset_class").asText() shouldBe "ACOES"
                body.get("targets").get("sub_classes").get(0).get("sub_class").asText() shouldBe "FLOATING_RATE"
                body.get("targets").get("tickers").get(0).get("ticker").asText() shouldBe "VALE3"
            }
        }

        it("404s when no month is open") {
            val service = mockk<MonthService>()
            coEvery { service.fetchCurrent() } returns null

            testApplication {
                application { installRoutes(MonthController(service)) }

                client.get("/month/current").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("opening a month") {

        it("creates an open month from the period, capital and floor") {
            val service = mockk<MonthService>()
            coEvery { service.open(any()) } returns currentMonth

            testApplication {
                application { installRoutes(MonthController(service)) }

                val response = client.post("/month") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"period":"2026-09","capital":245000.00,"floor":20000.00}""")
                }

                response.status shouldBe HttpStatusCode.Created
                coVerify(exactly = 1) { service.open(match { it.period == YearMonth.parse("2026-09") }) }
            }
        }
    }

    describe("updating the current month") {

        it("updates the capital and floor") {
            val service = mockk<MonthService>()
            coEvery { service.updateCurrent(any()) } returns currentMonth

            testApplication {
                application { installRoutes(MonthController(service)) }

                val response = client.post("/month/current") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"capital":250000.00,"floor":25000.00}""")
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) {
                    service.updateCurrent(match { it.capital.compareTo(BigDecimal("250000")) == 0 })
                }
            }
        }

        it("404s when no month is open") {
            val service = mockk<MonthService>()
            coEvery { service.updateCurrent(any()) } returns null

            testApplication {
                application { installRoutes(MonthController(service)) }

                val response = client.post("/month/current") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"capital":250000.00,"floor":25000.00}""")
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("managing the target chain") {

        it("reads the current targets") {
            val service = mockk<MonthService>()
            coEvery { service.fetchCurrentTargets() } returns targets

            testApplication {
                application { installRoutes(MonthController(service)) }

                val response = client.get("/month/current/targets")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("tickers").get(0).get("ticker").asText() shouldBe "VALE3"
            }
        }

        it("404s when no month is open") {
            val service = mockk<MonthService>()
            coEvery { service.fetchCurrentTargets() } returns null

            testApplication {
                application { installRoutes(MonthController(service)) }

                client.get("/month/current/targets").status shouldBe HttpStatusCode.NotFound
            }
        }

        it("replaces the whole chain") {
            val service = mockk<MonthService>()
            coEvery { service.replaceCurrentTargets(any()) } returns currentMonth

            testApplication {
                application { installRoutes(MonthController(service)) }

                val response = client.put("/month/current/targets") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "classes": [{"asset_class": "ACOES", "percentage": 50.00}],
                          "strategies": [],
                          "sub_classes": [],
                          "tickers": [{"strategy": "Top Dividendos", "ticker": "VALE3", "percentage": 12.00}]
                        }
                        """.trimIndent(),
                    )
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) {
                    service.replaceCurrentTargets(
                        match {
                            it.classes == listOf(ClassTarget(AssetClass.ACOES, BigDecimal("50.00"))) &&
                                it.tickers == listOf(vale3)
                        },
                    )
                }
            }
        }

        it("rejects strategy targets above 100% in a class as a domain error") {
            val service = mockk<MonthService>()
            coEvery { service.replaceCurrentTargets(any()) } throws
                StrategyTargetsExceedClassException(AssetClass.ACOES, BigDecimal("110.00"))

            testApplication {
                application { installRoutes(MonthController(service)) }

                val response = client.put("/month/current/targets") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "classes": [{"asset_class": "ACOES", "percentage": 100.00}],
                          "strategies": [
                            {"asset_class": "ACOES", "strategy": "Top Dividendos", "percentage": 60.00},
                            {"asset_class": "ACOES", "strategy": "Small Caps", "percentage": 50.00}
                          ],
                          "sub_classes": [],
                          "tickers": []
                        }
                        """.trimIndent(),
                    )
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("error").asText() shouldBe "strategy-targets-exceed-class"
            }
        }
    }
})

private val vale3 = TickerTarget(
    strategy = "Top Dividendos",
    ticker = "VALE3",
    percentage = BigDecimal("12.00"),
)

private val targets = Targets(
    classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("50.00"))),
    strategies = listOf(StrategyTarget(AssetClass.ACOES, "Top Dividendos", BigDecimal("34.00"))),
    subClasses = listOf(SubClassTarget(FixedIncomeSubClass.FLOATING_RATE, BigDecimal("55.00"))),
    tickers = listOf(vale3),
)

private val currentMonth = CurrentMonth(
    month = Month(
        id = 7,
        period = YearMonth.parse("2026-09"),
        capital = BigDecimal("245000.00"),
        floor = BigDecimal("20000.00"),
        status = MonthStatus.OPEN,
    ),
    targets = targets,
)
