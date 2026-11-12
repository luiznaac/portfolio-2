package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.portfolio.PortfolioStatusService
import dev.agner.portfolio.usecase.portfolio.model.AttentionItem
import dev.agner.portfolio.usecase.portfolio.model.AttentionKind
import dev.agner.portfolio.usecase.portfolio.model.AttentionObject
import dev.agner.portfolio.usecase.portfolio.model.AttentionSeverity
import dev.agner.portfolio.usecase.portfolio.model.MonthActionKind
import dev.agner.portfolio.usecase.portfolio.model.MonthBlock
import dev.agner.portfolio.usecase.portfolio.model.MonthNextAction
import dev.agner.portfolio.usecase.portfolio.model.MonthRef
import dev.agner.portfolio.usecase.portfolio.model.PortfolioDataBlock
import dev.agner.portfolio.usecase.portfolio.model.PortfolioStatus
import dev.agner.portfolio.usecase.portfolio.model.StatusRef
import dev.agner.portfolio.usecase.portfolio.model.StatusRefType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class PortfolioStatusControllerTest : DescribeSpec({

    describe("reading the portfolio status") {

        it("serves the typed aggregate with snake_case keys and an object reference") {
            val service = mockk<PortfolioStatusService>()
            coEvery { service.fetch() } returns status

            testApplication {
                application { installRoutes(PortfolioStatusController(service)) }

                val response = client.get("/portfolio/status")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("as_of").asText() shouldBe "2026-11-12"
                body.get("open_month").get("period").asText() shouldBe "2026-11"
                body.get("invested").decimalValue().compareTo(BigDecimal("6000")) shouldBe 0
                body.get("month").get("steps_done").asInt() shouldBe 4
                body.get("month").get("steps_total").asInt() shouldBe 6
                body.get("month").get("next_action").get("kind").asText() shouldBe "DECIDE_TRANSFER"

                val attention = body.get("attention").get(0)
                attention.get("kind").asText() shouldBe "PENDING_TRANSFER"
                attention.get("object").asText() shouldBe "MONTH"
                attention.get("severity").asText() shouldBe "ACTION"
                attention.get("ref").get("type").asText() shouldBe "TRANSFER"
                attention.get("ref").get("id").asInt() shouldBe 7
                attention.get("params").get("quantity").decimalValue()
                    .compareTo(BigDecimal("50")) shouldBe 0
                body.get("data").get("open_gaps").asInt() shouldBe 1
            }
        }
    }
})

private val status = PortfolioStatus(
    asOf = LocalDate.parse("2026-11-12"),
    openMonth = MonthRef(1, YearMonth.parse("2026-11")),
    lastClose = MonthRef(2, YearMonth.parse("2026-10")),
    capital = BigDecimal("10000"),
    invested = BigDecimal("6000"),
    uninvestedBalance = BigDecimal("4000.00"),
    deficit = BigDecimal("4000"),
    drift = BigDecimal("-40"),
    month = MonthBlock(
        open = true,
        stepsDone = 4,
        stepsTotal = 6,
        suggestedOrders = 1,
        pendingTransfers = 1,
        nextAction = MonthNextAction(
            kind = MonthActionKind.DECIDE_TRANSFER,
            ref = StatusRef(StatusRefType.TRANSFER, id = 7, ticker = "ITUB4"),
        ),
    ),
    data = PortfolioDataBlock(
        quotesAsOf = LocalDate.parse("2026-11-11"),
        staleQuotes = emptyList(),
        openGaps = 1,
        lastSnapshot = null,
    ),
    attention = listOf(
        AttentionItem(
            kind = AttentionKind.PENDING_TRANSFER,
            `object` = AttentionObject.MONTH,
            severity = AttentionSeverity.ACTION,
            ref = StatusRef(StatusRefType.TRANSFER, id = 7, ticker = "ITUB4"),
            params = mapOf("quantity" to BigDecimal("50"), "amount" to BigDecimal("500")),
        ),
    ),
)
