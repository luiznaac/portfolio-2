package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.tax.StockTaxService
import dev.agner.portfolio.usecase.tax.model.ExemptionMeter
import dev.agner.portfolio.usecase.tax.model.StockTaxReport
import dev.agner.portfolio.usecase.tax.model.TickerAveragePrice
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

class TaxControllerTest : DescribeSpec({

    describe("reading the current month's tax") {

        it("serves the meter and the average prices with snake_case keys") {
            val service = mockk<StockTaxService>()
            coEvery { service.fetchCurrent() } returns report

            testApplication {
                application { installRoutes(TaxController(service)) }

                val response = client.get("/month/current/tax")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("period").asText() shouldBe "2026-10"
                body.get("meter").get("gross_sales").decimal() shouldBe "20010"
                body.get("meter").get("exemption_limit").decimal() shouldBe "20000"
                body.get("meter").get("exceeded").asBoolean() shouldBe true
                body.get("meter").get("realized_gain").decimal() shouldBe "4002"
                body.get("meter").get("taxable_gain").decimal() shouldBe "4002"
                body.get("meter").get("rate").decimal() shouldBe "15"
                body.get("meter").get("tax").decimal() shouldBe "600.3"
                body.get("average_prices").get(0).get("ticker").asText() shouldBe "PETR4"
                body.get("average_prices").get(0).get("average_price").decimal() shouldBe "10"
            }
        }

        it("404s when no month is open") {
            val service = mockk<StockTaxService>()
            coEvery { service.fetchCurrent() } returns null

            testApplication {
                application { installRoutes(TaxController(service)) }

                client.get("/month/current/tax").status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})

private fun JsonNode.decimal(): String = decimalValue().stripTrailingZeros().toPlainString()

private val report = StockTaxReport(
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
)
