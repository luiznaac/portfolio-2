package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.closeStateFixture
import dev.agner.portfolio.usecase.monthFixture
import dev.agner.portfolio.usecase.monthlyclose.MonthlyCloseService
import dev.agner.portfolio.usecase.monthlyclose.PendingTransfersException
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.transferProposal
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

class MonthlyCloseControllerTest : DescribeSpec({

    describe("closing the current month") {

        it("serves the persisted snapshot") {
            val service = mockk<MonthlyCloseService>()
            coEvery { service.closeCurrent() } returns snapshot

            testApplication {
                application { installRoutes(MonthlyCloseController(service)) }

                val response = client.post("/month/current/close")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("month_id").asInt() shouldBe 7
                body.get("state").get("period").asText() shouldBe "2026-10"
                body.get("state").get("caixa").decimalValue().compareTo(BigDecimal("45000.00")) shouldBe 0
                body.get("state").get("tax").get("meter").get("exemption_limit")
                    .decimalValue().compareTo(BigDecimal("20000.00")) shouldBe 0
            }
        }

        it("404s when no month is open") {
            val service = mockk<MonthlyCloseService>()
            coEvery { service.closeCurrent() } returns null

            testApplication {
                application { installRoutes(MonthlyCloseController(service)) }

                client.post("/month/current/close").status shouldBe HttpStatusCode.NotFound
            }
        }

        it("409s with the pending transfers when the inbox is undecided") {
            val service = mockk<MonthlyCloseService>()
            coEvery { service.closeCurrent() } throws PendingTransfersException(listOf(transferProposal()))

            testApplication {
                application { installRoutes(MonthlyCloseController(service)) }

                val response = client.post("/month/current/close")

                response.status shouldBe HttpStatusCode.Conflict
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("error").asText() shouldBe "pending-transfers"
                body.get("pending_transfers").get(0).get("id").asInt() shouldBe 7
                body.get("pending_transfers").get(0).get("ticker").asText() shouldBe "ITUB4"
                body.get("pending_transfers").get(0).get("from_strategy").asText() shouldBe "Small Caps"
                body.get("pending_transfers").get(0).get("to_strategy").asText() shouldBe "Top Dividendos"
            }
        }
    }

    describe("listing closed months") {

        it("serves the closed months") {
            val service = mockk<MonthlyCloseService>()
            coEvery { service.fetchClosedMonths() } returns listOf(monthFixture(MonthStatus.CLOSED))

            testApplication {
                application { installRoutes(MonthlyCloseController(service)) }

                val response = client.get("/months")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get(0).get("period").asText() shouldBe "2026-10"
                body.get(0).get("status").asText() shouldBe "CLOSED"
            }
        }
    }
})

private val snapshot = Snapshot(
    id = 9,
    monthId = 7,
    state = closeStateFixture(),
    createdAt = LocalDateTime.parse("2026-10-31T18:00:00"),
)
