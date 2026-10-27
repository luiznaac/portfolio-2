package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.listedasset.AttributionService
import dev.agner.portfolio.usecase.listedasset.InvalidAttributionException
import dev.agner.portfolio.usecase.listedasset.InvalidTransferException
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlice
import dev.agner.portfolio.usecase.listedasset.model.AttributionSliceCreation
import dev.agner.portfolio.usecase.listedasset.model.AttributionSummary
import dev.agner.portfolio.usecase.listedasset.model.AttributionTransferCreation
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal

class AttributionControllerTest : DescribeSpec({

    describe("reading a position's attribution") {

        it("returns 200 with the custody, slice list and unattributed remainder") {
            val service = mockk<AttributionService>()
            coEvery { service.fetchSummary("PETR4") } returns summary

            testApplication {
                application { installRoutes(AttributionController(service)) }

                val response = client.get("/positions/PETR4/attribution")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("ticker").asText() shouldBe "PETR4"
                body.get("custody_quantity").decimalValue().compareTo(BigDecimal("100")) shouldBe 0
                body.get("attributed_quantity").decimalValue().compareTo(BigDecimal("60")) shouldBe 0
                body.get("unattributed_quantity").decimalValue().compareTo(BigDecimal("40")) shouldBe 0
                body.get("slices").get(0).get("strategy_id").asInt() shouldBe 1
                body.get("slices").get(0).get("strategy_name").asText() shouldBe "Top Dividendos"
                body.get("slices").get(0).get("quantity").decimalValue().compareTo(BigDecimal("60")) shouldBe 0
            }
        }

        it("returns 404 when the ticker has no position") {
            val service = mockk<AttributionService>()
            coEvery { service.fetchSummary("PETR4") } returns null

            testApplication {
                application { installRoutes(AttributionController(service)) }

                client.get("/positions/PETR4/attribution").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("setting an attribution slice") {

        it("returns 200 with the updated attribution and passes the body through") {
            val service = mockk<AttributionService>()
            coEvery { service.setSlice("PETR4", any()) } returns summary

            testApplication {
                application { installRoutes(AttributionController(service)) }

                val response = client.post("/positions/PETR4/attribution") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"strategy_id":1,"quantity":60}""")
                }

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get("unattributed_quantity").asText() shouldBe "40"
                coVerify(exactly = 1) {
                    service.setSlice(
                        "PETR4",
                        match { it == AttributionSliceCreation(strategyId = 1, quantity = BigDecimal("60")) },
                    )
                }
            }
        }

        it("returns 400 with the canonical error body when the slice exceeds the position") {
            val service = mockk<AttributionService>()
            coEvery { service.setSlice("PETR4", any()) } throws
                InvalidAttributionException("slice above custody")

            testApplication {
                application { installRoutes(AttributionController(service)) }

                val response = client.post("/positions/PETR4/attribution") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"strategy_id":1,"quantity":150}""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe "invalid-attribution"
            }
        }

        it("returns 404 when the position or strategy does not exist") {
            val service = mockk<AttributionService>()
            coEvery { service.setSlice("PETR4", any()) } returns null

            testApplication {
                application { installRoutes(AttributionController(service)) }

                val response = client.post("/positions/PETR4/attribution") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"strategy_id":1,"quantity":60}""")
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("transferring attribution") {

        it("returns 200 with the refreshed attribution and passes the body through") {
            val service = mockk<AttributionService>()
            coEvery { service.transfer("PETR4", any()) } returns summary

            testApplication {
                application { installRoutes(AttributionController(service)) }

                val response = client.post("/positions/PETR4/attribution/transfer") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"from_strategy_id":2,"to_strategy_id":1,"quantity":10}""")
                }

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get("attributed_quantity").asText() shouldBe "60"
                coVerify(exactly = 1) {
                    service.transfer(
                        "PETR4",
                        match {
                            it == AttributionTransferCreation(
                                fromStrategyId = 2,
                                toStrategyId = 1,
                                quantity = BigDecimal("10"),
                            )
                        },
                    )
                }
            }
        }

        it("returns 404 when the position or a strategy does not exist") {
            val service = mockk<AttributionService>()
            coEvery { service.transfer("PETR4", any()) } returns null

            testApplication {
                application { installRoutes(AttributionController(service)) }

                val response = client.post("/positions/PETR4/attribution/transfer") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"from_strategy_id":2,"to_strategy_id":1,"quantity":10}""")
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        it("returns 400 with the canonical error body when the transfer is invalid") {
            val service = mockk<AttributionService>()
            coEvery { service.transfer("PETR4", any()) } throws
                InvalidTransferException("slice above custody")

            testApplication {
                application { installRoutes(AttributionController(service)) }

                val response = client.post("/positions/PETR4/attribution/transfer") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"from_strategy_id":2,"to_strategy_id":1,"quantity":10}""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe "invalid-transfer"
            }
        }
    }

    describe("removing an attribution slice") {

        it("returns 200 with the updated attribution") {
            val service = mockk<AttributionService>()
            coEvery { service.removeSlice("PETR4", 1) } returns summary

            testApplication {
                application { installRoutes(AttributionController(service)) }

                val response = client.delete("/positions/PETR4/attribution/1")

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) { service.removeSlice("PETR4", 1) }
            }
        }

        it("returns 404 when the ticker has no position") {
            val service = mockk<AttributionService>()
            coEvery { service.removeSlice("PETR4", 1) } returns null

            testApplication {
                application { installRoutes(AttributionController(service)) }

                client.delete("/positions/PETR4/attribution/1").status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})

private val summary = AttributionSummary(
    ticker = "PETR4",
    custodyQuantity = BigDecimal("100"),
    attributedQuantity = BigDecimal("60"),
    unattributedQuantity = BigDecimal("40"),
    slices = listOf(
        AttributionSlice(
            strategyId = 1,
            strategyName = "Top Dividendos",
            quantity = BigDecimal("60"),
        ),
    ),
)
