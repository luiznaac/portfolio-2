package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.listedasset.DuplicateTradeException
import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerCursor
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerFilter
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerPage
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

    describe("listing the ledger") {

        it("passes the ticker, date window, side and cursor to the service") {
            val service = mockk<TradeService>()
            val captured = slot<TradeLedgerFilter>()
            coEvery { service.fetchLedger(capture(captured)) } returns
                ledgerPage()

            testApplication {
                application { installRoutes(TradeController(service)) }

                val response = client.get(
                    "/trades/ledger?ticker=petr4&from=2026-10-01&to=2026-10-31&side=BUY&cursor=2026-10-02:2",
                )

                response.status shouldBe HttpStatusCode.OK
                captured.captured shouldBe TradeLedgerFilter(
                    ticker = "petr4",
                    from = LocalDate.parse("2026-10-01"),
                    to = LocalDate.parse("2026-10-31"),
                    side = TradeSide.BUY,
                    after = TradeLedgerCursor(LocalDate.parse("2026-10-02"), 2),
                )
            }
        }

        it("returns the page with its next cursor and the filtered total") {
            val service = mockk<TradeService>()
            coEvery { service.fetchLedger(any()) } returns
                ledgerPage()

            testApplication {
                application { installRoutes(TradeController(service)) }

                val body = ObjectMapper().readTree(client.get("/trades/ledger").bodyAsText())

                body.get("rows").get(0).get("ticker").asText() shouldBe "PETR4"
                body.get("next_cursor").asText() shouldBe "2026-10-02:2"
                body.get("total").asInt() shouldBe 3
                body.get("financial_total").decimalValue()
                    .compareTo(BigDecimal("4892.00")) shouldBe 0
            }
        }

        it("rejects an unknown side, a malformed date and a malformed cursor with the canonical 400 body") {
            val service = mockk<TradeService>()

            testApplication {
                application { installRoutes(TradeController(service)) }

                listOf(
                    "/trades/ledger?side=HOLD",
                    "/trades/ledger?from=10-01-2026",
                    "/trades/ledger?cursor=not-a-cursor",
                ).forEach { path ->
                    val response = client.get(path)

                    response.status shouldBe HttpStatusCode.BadRequest
                    ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                        "invalid-parameter"
                }
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

private fun ledgerPage() = TradeLedgerPage(
    listOf(manualTrade),
    nextCursor = "2026-10-02:2",
    total = 3,
    financialTotal = BigDecimal("4892.00"),
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
