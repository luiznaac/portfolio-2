package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.harvestLeg
import dev.agner.portfolio.usecase.portfolio.model.MonthPeriodRange
import dev.agner.portfolio.usecase.tax.HarvestService
import dev.agner.portfolio.usecase.tax.StockTaxService
import dev.agner.portfolio.usecase.tax.TaxPeriodOutsideWindowException
import dev.agner.portfolio.usecase.tax.model.CarryBucket
import dev.agner.portfolio.usecase.tax.model.DayTradeWarning
import dev.agner.portfolio.usecase.tax.model.ExemptionMeter
import dev.agner.portfolio.usecase.tax.model.FiiHarvestReport
import dev.agner.portfolio.usecase.tax.model.HarvestIgnored
import dev.agner.portfolio.usecase.tax.model.HarvestPair
import dev.agner.portfolio.usecase.tax.model.HarvestProposal
import dev.agner.portfolio.usecase.tax.model.HarvestReport
import dev.agner.portfolio.usecase.tax.model.RealizedKind
import dev.agner.portfolio.usecase.tax.model.RealizedResult
import dev.agner.portfolio.usecase.tax.model.StockTaxReport
import dev.agner.portfolio.usecase.tax.model.TaxPendingGap
import dev.agner.portfolio.usecase.tax.model.TaxUnclassifiedStatus
import dev.agner.portfolio.usecase.tax.model.TaxUnclassifiedTicker
import dev.agner.portfolio.usecase.tax.model.TickerAveragePrice
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class TaxControllerTest : DescribeSpec({

    describe("reading a period's tax") {

        it("serves the meter, average prices, realized result, carry state and DARF deadline") {
            val service = mockk<StockTaxService>()
            coEvery { service.fetch(YearMonth.parse("2026-10")) } returns taxReport

            testApplication {
                application { installRoutes(TaxController(service, mockk())) }

                val response = client.get("/tax/2026-10")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("period").asText() shouldBe "2026-10"
                body.get("darf_due_date").asText() shouldBe "2026-11-30"
                body.get("meter").get("gross_sales").decimal() shouldBe "20010"
                body.get("meter").get("exemption_limit").decimal() shouldBe "20000"
                body.get("meter").get("exceeded").asBoolean() shouldBe true
                body.get("meter").get("realized_gain").decimal() shouldBe "4002"
                body.get("meter").get("taxable_gain").decimal() shouldBe "4002"
                body.get("meter").get("rate").decimal() shouldBe "15"
                body.get("meter").get("tax").decimal() shouldBe "600.3"
                body.get("average_prices").get(0).get("ticker").asText() shouldBe "PETR4"
                body.get("average_prices").get(0).get("average_price").decimal() shouldBe "10"
                body.get("realized").get(0).get("kind").asText() shouldBe "SWING"
                body.get("realized").get(0).get("gross_sales").decimal() shouldBe "20010"
                body.get("realized").get(0).get("result").decimal() shouldBe "4002"
                body.get("carry").get(0).get("kind").asText() shouldBe "SWING"
                body.get("carry").get(0).get("available").asBoolean() shouldBe true
                body.get("carry").get(0).get("opening_balance").decimal() shouldBe "500"
                body.get("carry").get(0).get("loss_applied").decimal() shouldBe "500"
                body.get("carry").get(0).get("closing_balance").decimal() shouldBe "0"
                body.get("day_trades").get(0).get("ticker").asText() shouldBe "PETR4"
                body.get("day_trades").get(0).get("institution_id").asInt() shouldBe 3
                body.get("day_trades").get(0).get("date").asText() shouldBe "2026-10-05"
                body.get("pending_gaps").get(0).get("ticker").asText() shouldBe "VALE3"
                body.get("unclassified").get(0).get("ticker").asText() shouldBe "MYST3"
                body.get("unclassified").get(0).get("status").asText() shouldBe "HELD_AND_SOLD"
            }
        }

        it("serializes an unavailable carry bucket with explicit nulls") {
            val service = mockk<StockTaxService>()
            coEvery { service.fetch(any()) } returns taxReport.copy(
                carry = listOf(
                    CarryBucket(
                        kind = RealizedKind.SWING,
                        available = false,
                        openingBalance = null,
                        lossBanked = null,
                        lossApplied = null,
                        closingBalance = null,
                    ),
                ),
            )

            testApplication {
                application { installRoutes(TaxController(service, mockk())) }

                val body = ObjectMapper().readTree(client.get("/tax/2020-12").bodyAsText())
                val bucket = body.get("carry").get(0)

                bucket.get("available").asBoolean() shouldBe false
                bucket.has("opening_balance") shouldBe true
                bucket.get("opening_balance").isNull shouldBe true
                bucket.has("closing_balance") shouldBe true
            }
        }

        it("maps a malformed period to invalid-parameter without calling the service") {
            val service = mockk<StockTaxService>()

            testApplication {
                application { installRoutes(TaxController(service, mockk())) }

                val response = client.get("/tax/not-a-month")

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                    "invalid-parameter"
                coVerify(exactly = 0) { service.fetch(any()) }
            }
        }

        it("rejects a period outside the window") {
            val service = mockk<StockTaxService>()
            coEvery { service.fetch(any()) } throws TaxPeriodOutsideWindowException(
                period = YearMonth.parse("2020-02"),
                range = MonthPeriodRange(
                    min = YearMonth.parse("2020-03"),
                    max = YearMonth.parse("2026-10"),
                    default = YearMonth.parse("2020-12"),
                ),
            )

            testApplication {
                application { installRoutes(TaxController(service, mockk())) }

                val response = client.get("/tax/2020-02")

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                    "tax-period-outside-window"
            }
        }
    }

    describe("reading the harvest pass") {

        it("serves the suggestions, ignored tickers, FII report and accepted pairs") {
            val harvest = mockk<HarvestService>()
            coEvery { harvest.fetch() } returns harvestReport

            testApplication {
                application { installRoutes(TaxController(mockk(), harvest)) }

                val response = client.get("/tax/harvest")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("period").asText() shouldBe "2026-10"
                body.get("headroom").decimal() shouldBe "13900"
                body.get("minimum_loss").decimal() shouldBe "500"
                body.get("suggestions").get(0).get("ticker").asText() shouldBe "PETR4"
                body.get("suggestions").get(0).get("strategy").asText() shouldBe "Dividendos"
                body.get("suggestions").get(0).get("loss").decimal() shouldBe "600"
                body.get("suggestions").get(0).get("neutro").asBoolean() shouldBe true
                body.get("suggestions").get(0).get("sell").get("date").asText() shouldBe "2026-10-22"
                body.get("suggestions").get(0).get("sell").get("amount").decimal() shouldBe "3000"
                body.get("suggestions").get(0).get("rebuy").get("date").asText() shouldBe "2026-10-23"
                body.get("ignored").get(0).get("ticker").asText() shouldBe "VALE3"
                body.get("fii_report").get(0).get("ticker").asText() shouldBe "HGLG11"
                body.get("fii_report").get(0).get("estimated_tax").decimal() shouldBe "200"
                body.get("pairs").get(0).get("ticker").asText() shouldBe "PETR4"
                body.get("pairs").get(0).get("strategy").asText() shouldBe "Dividendos"
            }
        }

        it("404s when no month is open") {
            val harvest = mockk<HarvestService>()
            coEvery { harvest.fetch() } returns null

            testApplication {
                application { installRoutes(TaxController(mockk(), harvest)) }

                client.get("/tax/harvest").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("accepting a harvest pair") {

        it("passes both editable legs through and returns 201") {
            val harvest = mockk<HarvestService>()
            coEvery { harvest.accept(any()) } returns harvestReport

            testApplication {
                application { installRoutes(TaxController(mockk(), harvest)) }

                val response = client.post("/tax/harvest") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {"ticker":"petr4","strategy_id":7,
                         "sell_date":"2026-10-22","sell_quantity":300,"sell_price":10,
                         "rebuy_date":"2026-10-23","rebuy_quantity":300,"rebuy_price":10}
                        """.trimIndent(),
                    )
                }

                response.status shouldBe HttpStatusCode.Created
                coVerify(exactly = 1) {
                    harvest.accept(
                        match {
                            it.ticker == "petr4" &&
                                it.strategyId == 7 &&
                                it.sellDate == LocalDate.parse("2026-10-22") &&
                                it.rebuyDate == LocalDate.parse("2026-10-23") &&
                                it.sellQuantity.compareTo(BigDecimal("300")) == 0 &&
                                it.rebuyPrice.compareTo(BigDecimal("10")) == 0
                        },
                    )
                }
            }
        }

        it("404s when no month is open") {
            val harvest = mockk<HarvestService>()
            coEvery { harvest.accept(any()) } returns null

            testApplication {
                application { installRoutes(TaxController(mockk(), harvest)) }

                val response = client.post("/tax/harvest") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {"ticker":"PETR4","sell_date":"2026-10-22","sell_quantity":300,"sell_price":10,
                         "rebuy_date":"2026-10-23","rebuy_quantity":300,"rebuy_price":10}
                        """.trimIndent(),
                    )
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("toggling a ticker's harvest opt-out") {

        it("writes the refusal and returns the fresh report") {
            val harvest = mockk<HarvestService>()
            coEvery { harvest.setOptOut("PETR4", true) } returns harvestReport

            testApplication {
                application { installRoutes(TaxController(mockk(), harvest)) }

                val response = client.put("/tax/harvest/PETR4/opt-out") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"opted_out":true}""")
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) { harvest.setOptOut("PETR4", true) }
            }
        }

        it("404s when no month is open") {
            val harvest = mockk<HarvestService>()
            coEvery { harvest.setOptOut(any(), any()) } returns null

            testApplication {
                application { installRoutes(TaxController(mockk(), harvest)) }

                val response = client.put("/tax/harvest/PETR4/opt-out") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"opted_out":true}""")
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})

private fun JsonNode.decimal(): String = decimalValue().stripTrailingZeros().toPlainString()

private val taxReport = StockTaxReport(
    period = YearMonth.parse("2026-10"),
    meter = ExemptionMeter(
        grossSales = BigDecimal("20010"),
        exemptionLimit = BigDecimal("20000"),
        exceeded = true,
        realizedGain = BigDecimal("4002"),
        taxableGain = BigDecimal("4002"),
        rate = BigDecimal("15"),
        tax = BigDecimal("600.3"),
    ),
    averagePrices = listOf(
        TickerAveragePrice(
            ticker = "PETR4",
            quantity = BigDecimal("100"),
            averagePrice = BigDecimal("10"),
        ),
    ),
    realized = listOf(
        RealizedResult(
            kind = RealizedKind.SWING,
            grossSales = BigDecimal("20010"),
            gains = BigDecimal("4002"),
            losses = BigDecimal("0"),
            result = BigDecimal("4002"),
        ),
    ),
    carry = listOf(
        CarryBucket(
            kind = RealizedKind.SWING,
            available = true,
            openingBalance = BigDecimal("500"),
            lossBanked = BigDecimal("0"),
            lossApplied = BigDecimal("500"),
            closingBalance = BigDecimal("0"),
        ),
    ),
    dayTrades = listOf(
        DayTradeWarning(
            ticker = "PETR4",
            institutionId = 3,
            date = LocalDate.parse("2026-10-05"),
            quantity = BigDecimal("100"),
        ),
    ),
    pendingGaps = listOf(
        TaxPendingGap(
            id = 4,
            ticker = "VALE3",
            date = LocalDate.parse("2026-10-12"),
            quantity = BigDecimal("50"),
        ),
    ),
    unclassified = listOf(
        TaxUnclassifiedTicker(ticker = "MYST3", status = TaxUnclassifiedStatus.HELD_AND_SOLD),
    ),
)

private val sellLeg = harvestLeg("2026-10-22")
private val rebuyLeg = harvestLeg("2026-10-23")

private val harvestReport = HarvestReport(
    period = YearMonth.parse("2026-10"),
    headroom = BigDecimal("13900"),
    minimumLoss = BigDecimal("500"),
    suggestions = listOf(
        HarvestProposal(
            ticker = "PETR4",
            strategyId = 7,
            strategy = "Dividendos",
            quantity = BigDecimal("300"),
            averagePrice = BigDecimal("12"),
            price = BigDecimal("10"),
            loss = BigDecimal("600"),
            neutro = true,
            sell = sellLeg,
            rebuy = rebuyLeg,
        ),
    ),
    ignored = listOf(
        HarvestIgnored(
            ticker = "VALE3",
            strategyId = null,
            strategy = null,
            quantity = BigDecimal("100"),
            loss = BigDecimal("600"),
            neutro = false,
        ),
    ),
    fiiReport = listOf(
        FiiHarvestReport(
            ticker = "HGLG11",
            quantity = BigDecimal("100"),
            price = BigDecimal("110"),
            averagePrice = BigDecimal("100"),
            unrealizedResult = BigDecimal("1000"),
            estimatedTax = BigDecimal("200"),
        ),
    ),
    pairs = listOf(
        HarvestPair(
            id = 1,
            ticker = "PETR4",
            strategyId = 7,
            strategy = "Dividendos",
            loss = BigDecimal("600"),
            sell = sellLeg,
            rebuy = rebuyLeg,
        ),
    ),
)
