package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.configuration.DefaultDomainExceptionStatusMapper
import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.order.InvalidTransferQuantityException
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.TransferProposalNotFoundException
import dev.agner.portfolio.usecase.order.TransferProposalNotPendingException
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.APPLIED
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.PENDING
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.REJECTED
import dev.agner.portfolio.usecase.order.model.TransferSettings
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class OrderControllerTest : DescribeSpec({

    describe("listing the month's transfers") {

        it("returns 200 with the proposals from transfersForMonth") {
            val planService = mockk<OrderPlanService>(relaxed = true)
            coEvery { planService.transfersForMonth() } returns emptyList()

            testApplication {
                application { installController(OrderController(planService)) }

                val response = client.get("/orders/transfers")

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) { planService.transfersForMonth() }
            }
        }
    }

    describe("transfer settings") {

        it("returns 200 with the current settings") {
            val planService = mockk<OrderPlanService>(relaxed = true)
            coEvery { planService.transferSettings() } returns TransferSettings(BigDecimal("100.00"))

            testApplication {
                application { installController(OrderController(planService)) }

                val response = client.get("/orders/transfers/settings")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldContain "100"
            }
        }

        it("passes the threshold from the body to setTransferSettings") {
            val planService = mockk<OrderPlanService>(relaxed = true)
            coEvery { planService.setTransferSettings(any()) } returns TransferSettings(BigDecimal("100"))

            testApplication {
                application { installController(OrderController(planService)) }

                val response = client.put("/orders/transfers/settings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"auto_approval_threshold": 100}""")
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) { planService.setTransferSettings(BigDecimal("100")) }
            }
        }
    }

    describe("approving and rejecting a transfer") {

        it("passes the explicit quantity to approveTransfer") {
            val planService = mockk<OrderPlanService>(relaxed = true)
            coEvery { planService.approveTransfer(any(), any()) } returns proposal(APPLIED)

            testApplication {
                application { installController(OrderController(planService)) }

                val response = client.post("/orders/transfers/7/approve") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"quantity": 5}""")
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) { planService.approveTransfer(7, BigDecimal("5")) }
            }
        }

        it("passes a null quantity when the body carries none") {
            val planService = mockk<OrderPlanService>(relaxed = true)
            coEvery { planService.approveTransfer(any(), null) } returns proposal(APPLIED)

            testApplication {
                application { installController(OrderController(planService)) }

                val response = client.post("/orders/transfers/7/approve") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) { planService.approveTransfer(7, null) }
            }
        }

        it("delegates reject to rejectTransfer") {
            val planService = mockk<OrderPlanService>(relaxed = true)
            coEvery { planService.rejectTransfer(any()) } returns proposal(REJECTED)

            testApplication {
                application { installController(OrderController(planService)) }

                val response = client.post("/orders/transfers/7/reject")

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) { planService.rejectTransfer(7) }
            }
        }
    }

    describe("domain error mapping") {

        it("returns 404 when the proposal does not exist") {
            val planService = mockk<OrderPlanService>(relaxed = true)
            coEvery { planService.approveTransfer(any(), any()) } throws TransferProposalNotFoundException(7)

            testApplication {
                application { installController(OrderController(planService)) }

                val response = client.post("/orders/transfers/7/approve") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"quantity": 5}""")
                }
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.NotFound
                payload.get("error").asText() shouldBe "transfer-proposal-not-found"
                payload.get("detail").asText() shouldContain "7"
            }
        }

        it("returns 409 when the proposal is not pending") {
            val planService = mockk<OrderPlanService>(relaxed = true)
            coEvery { planService.approveTransfer(any(), any()) } throws
                TransferProposalNotPendingException(7, APPLIED)

            testApplication {
                application { installController(OrderController(planService)) }

                val response = client.post("/orders/transfers/7/approve") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"quantity": 5}""")
                }
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.Conflict
                payload.get("error").asText() shouldBe "transfer-proposal-not-pending"
            }
        }

        it("returns 400 when the quantity is out of range") {
            val planService = mockk<OrderPlanService>(relaxed = true)
            coEvery { planService.approveTransfer(any(), any()) } throws
                InvalidTransferQuantityException(BigDecimal("50"), BigDecimal("9"))

            testApplication {
                application { installController(OrderController(planService)) }

                val response = client.post("/orders/transfers/7/approve") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"quantity": 50}""")
                }
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.BadRequest
                payload.get("error").asText() shouldBe "invalid-transfer-quantity"
            }
        }

        it("returns 400 for a non-numeric transfer id without calling the service") {
            val planService = mockk<OrderPlanService>(relaxed = true)

            testApplication {
                application { installController(OrderController(planService)) }

                val response = client.post("/orders/transfers/abc/approve") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.BadRequest
                payload.get("error").asText() shouldBe "invalid-parameter"
                payload.get("detail").asText() shouldContain "transfer_id"
                coVerify(exactly = 0) { planService.approveTransfer(any(), any()) }
            }
        }
    }
})

private fun proposal(status: TransferProposalStatus) = TransferProposal(
    id = 7,
    month = LocalDate.parse("2026-09-01"),
    listedAssetId = 10,
    ticker = "PETR4",
    fromStrategyId = 1,
    fromStrategyName = "Top",
    toStrategyId = 2,
    toStrategyName = "Dividendos",
    proposedQuantity = BigDecimal("5"),
    appliedQuantity = if (status == PENDING) null else BigDecimal("5"),
    status = status,
    decidedAt = null,
)

private data class OrderTestApiError(val error: String, val message: String, val detail: String)

private fun Application.installController(controller: OrderController) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JsonMapper.mapper))
    }

    install(StatusPages) {
        exception<DomainException> { call, cause ->
            call.respond(
                DefaultDomainExceptionStatusMapper().statusFor(cause),
                OrderTestApiError(error = cause.error, message = cause.userMessage, detail = cause.detail),
            )
        }
    }

    routing(controller.routes())
}
