package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.history.BackfillService
import dev.agner.portfolio.usecase.history.model.BackfillReport
import dev.agner.portfolio.usecase.listedasset.GapService
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class HistoryControllerTest : DescribeSpec({

    describe("backfilling the history") {

        it("absorbs the seeds and reports the outcome") {
            val service = mockk<BackfillService>()
            coEvery { service.backfill(any()) } returns BackfillReport(
                absorbedSeeds = 2,
                adjustedSeeds = 1,
                gapsCreated = 3,
                gapsReplaced = 1,
                positionsReprojected = 4,
            )

            testApplication {
                application {
                    installRoutes(
                        HistoryController(
                            service,
                            mockk<dev.agner.portfolio.httpapi.import.B3PositionParser>(),
                            mockk(relaxed = true),
                        ),
                    )
                }

                val response = client.post("/history/backfill")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("absorbed_seeds").asInt() shouldBe 2
                body.get("adjusted_seeds").asInt() shouldBe 1
                body.get("gaps_created").asInt() shouldBe 3
                body.get("gaps_replaced").asInt() shouldBe 1
                body.get("positions_reprojected").asInt() shouldBe 4
            }
        }
    }

    describe("reading the gap records") {

        it("lists the open and replaced records") {
            val gaps = mockk<GapService>(relaxed = true)
            coEvery { gaps.fetchAll() } returns listOf(gap())

            testApplication {
                application { installRoutes(HistoryController(mockk(), mockk(), gaps)) }

                val response = client.get("/gaps")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get(0).get("ticker").asText() shouldBe "PETR4"
                body.get(0).get("quantity").decimalValue().compareTo(BigDecimal("40")) shouldBe 0
                body.get(0).get("status").asText() shouldBe "OPEN"
                body.get(0).get("realized_pending").asBoolean() shouldBe true
            }
        }
    }

    describe("pricing a gap") {

        it("serves the priced record") {
            val gaps = mockk<GapService>(relaxed = true)
            coEvery { gaps.price(9, BigDecimal("18")) } returns (gap() as Gap.Sell).copy(unitPrice = BigDecimal("18"))

            testApplication {
                application { installRoutes(HistoryController(mockk(), mockk(), gaps)) }

                val response = client.post("/gaps/9/price") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"unit_price":18}""")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("unit_price").decimalValue().compareTo(BigDecimal("18")) shouldBe 0
                body.get("realized_pending").asBoolean() shouldBe false
            }
        }
    }

    describe("seeding adoption openings") {

        it("serves the created seeds") {
            val service = mockk<BackfillService>()
            coEvery { service.seedOpenings(any()) } returns emptyList()

            testApplication {
                application { installRoutes(HistoryController(service, mockk(), mockk(relaxed = true))) }

                val response = client.post("/history/openings") {
                    contentType(ContentType.Application.Json)
                    setBody("""[]""")
                }

                response.status shouldBe HttpStatusCode.OK
            }
        }
    }
})

private fun gap(): Gap = Gap.Sell(
    id = 9,
    ticker = "PETR4",
    date = LocalDate.parse("2026-10-15"),
    quantity = BigDecimal("40"),
    unitPrice = null,
    status = GapStatus.OPEN,
)
