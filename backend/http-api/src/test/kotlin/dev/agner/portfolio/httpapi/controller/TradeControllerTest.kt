package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.listedasset.DuplicateTradeException
import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
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
import io.mockk.slot
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class TradeControllerTest : DescribeSpec({

    describe("listing trades") {

        it("returns 200 with the trades") {
            val service = mockk<TradeService>()
            coEvery { service.fetchAll() } returns listOf(manualTrade)

            testApplication {
                application { installRoutes(TradeController(service)) }

                val response = client.get("/trades")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get(0).get("ticker").asText() shouldBe "PETR4"
                ObjectMapper().readTree(response.bodyAsText()).get(0).get("side").asText() shouldBe "BUY"
            }
        }
    }

    describe("creating a manual trade") {

        it("returns 201 with the created trade and passes the request body through") {
            val service = mockk<TradeService>()
            val captured = slot<TradeCreation>()
            coEvery { service.createManual(capture(captured)) } returns manualTrade

            testApplication {
                application { installRoutes(TradeController(service)) }

                val response = client.post("/trades") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "ticker": "PETR4",
                          "side": "BUY",
                          "date": "2026-10-14",
                          "venue": "VISTA",
                          "quantity": 100,
                          "unit_price": 48.92,
                          "institution_id": 3
                        }
                        """.trimIndent(),
                    )
                }

                response.status shouldBe HttpStatusCode.Created
                ObjectMapper().readTree(response.bodyAsText()).get("source").asText() shouldBe "MANUAL"
                captured.captured shouldBe TradeCreation(
                    ticker = "PETR4",
                    side = TradeSide.BUY,
                    date = LocalDate.parse("2026-10-14"),
                    venue = "VISTA",
                    quantity = BigDecimal("100"),
                    unitPrice = BigDecimal("48.92"),
                    institutionId = 3,
                )
            }
        }

        it("returns 400 with the canonical error body when the manual trade dupes an import") {
            val service = mockk<TradeService>()
            coEvery { service.createManual(any()) } throws DuplicateTradeException(creation().naturalKey())

            testApplication {
                application { installRoutes(TradeController(service)) }

                val response = client.post("/trades") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "ticker": "PETR4",
                          "side": "BUY",
                          "date": "2026-10-14",
                          "venue": "VISTA",
                          "quantity": 100,
                          "unit_price": 48.92,
                          "institution_id": 3
                        }
                        """.trimIndent(),
                    )
                }

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe "duplicate-trade"
            }
        }
    }
})

private fun creation() = TradeCreation(
    ticker = "PETR4",
    side = TradeSide.BUY,
    date = LocalDate.parse("2026-10-14"),
    venue = "VISTA",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("48.92"),
    institutionId = 3,
)

private val manualTrade = Trade.Buy(
    id = 7,
    ticker = "PETR4",
    date = LocalDate.parse("2026-10-14"),
    venue = "VISTA",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("48.92"),
    institutionId = 3,
    fee = null,
    source = TradeSource.MANUAL,
    confirmedByB3 = false,
    ordinal = 1,
)
