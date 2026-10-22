package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.order.OrderLifecycleService
import dev.agner.portfolio.usecase.order.model.InvalidOrderMatchException
import dev.agner.portfolio.usecase.order.model.OrderList
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.SuggestedOrder
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferStatus
import dev.agner.portfolio.usecase.order.model.UnplannedTrade
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

class OrdersControllerTest : DescribeSpec({

    describe("reading the persisted order list") {

        it("serves the month's orders, transfer inbox and unplanned trades in snake_case") {
            val service = mockk<OrderLifecycleService>()
            coEvery { service.fetch() } returns orderList()

            testApplication {
                application { installRoutes(OrdersController(service)) }

                val response = client.get("/orders")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("period").asText() shouldBe "2026-10"

                val order = body.get("orders").get(0)
                order.get("ticker").asText() shouldBe "ITUB4"
                order.get("side").asText() shouldBe "BUY"
                order.get("suggested_price").decimalValue().compareTo(BigDecimal("10")) shouldBe 0
                order.get("executed_quantity").decimalValue().compareTo(BigDecimal("70")) shouldBe 0
                order.get("status").asText() shouldBe "EXECUTED"

                val transfer = body.get("transfers").get(0)
                transfer.get("from_strategy").asText() shouldBe "Small"
                transfer.get("to_strategy").asText() shouldBe "Top"
                transfer.get("auto_approved").asBoolean() shouldBe false
                transfer.get("status").asText() shouldBe "PENDING"

                body.get("unplanned_trades").get(0).get("ticker").asText() shouldBe "CURY3"
            }
        }

        it("404s when no month is open") {
            val service = mockk<OrderLifecycleService>()
            coEvery { service.fetch() } returns null

            testApplication {
                application { installRoutes(OrdersController(service)) }

                client.get("/orders").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("writing the plan") {

        it("refreshes and returns the persisted plan") {
            val service = mockk<OrderLifecycleService>()
            coEvery { service.refresh() } returns orderList()

            testApplication {
                application { installRoutes(OrdersController(service)) }

                val response = client.post("/orders/plan/refresh")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get("orders").size() shouldBe 1
                coVerify(exactly = 1) { service.refresh() }
            }
        }

        it("404s a refresh without an open month") {
            val service = mockk<OrderLifecycleService>()
            coEvery { service.refresh() } returns null

            testApplication {
                application { installRoutes(OrdersController(service)) }

                client.post("/orders/plan/refresh").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("deciding the transfer inbox") {

        it("approves a transfer") {
            val service = mockk<OrderLifecycleService>()
            coEvery { service.approveTransfer(7) } returns transfer(status = TransferStatus.APPROVED)

            testApplication {
                application { installRoutes(OrdersController(service)) }

                val response = client.post("/orders/transfers/7/approve")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get("status").asText() shouldBe "APPROVED"
            }
        }

        it("rejects a transfer") {
            val service = mockk<OrderLifecycleService>()
            coEvery { service.rejectTransfer(7) } returns transfer(status = TransferStatus.REJECTED)

            testApplication {
                application { installRoutes(OrdersController(service)) }

                val response = client.post("/orders/transfers/7/reject")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get("status").asText() shouldBe "REJECTED"
            }
        }

        it("404s an unknown transfer") {
            val service = mockk<OrderLifecycleService>()
            coEvery { service.approveTransfer(99) } returns null

            testApplication {
                application { installRoutes(OrdersController(service)) }

                client.post("/orders/transfers/99/approve").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("overriding a match") {

        it("retargets the order at the trade in the payload") {
            val service = mockk<OrderLifecycleService>()
            coEvery { service.overrideMatch(3, 9) } returns order(matchedTradeId = 9)

            testApplication {
                application { installRoutes(OrdersController(service)) }

                val response = client.put("/orders/3/match") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"trade_id": 9}""")
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify { service.overrideMatch(3, 9) }
            }
        }

        it("clears the match when trade_id is null") {
            val service = mockk<OrderLifecycleService>()
            coEvery { service.overrideMatch(3, null) } returns order()

            testApplication {
                application { installRoutes(OrdersController(service)) }

                val response = client.put("/orders/3/match") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"trade_id": null}""")
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify { service.overrideMatch(3, null) }
            }
        }

        it("surfaces a mismatched trade as the canonical error body") {
            val service = mockk<OrderLifecycleService>()
            coEvery { service.overrideMatch(3, 9) } throws
                InvalidOrderMatchException("trade 9 is VALE3, but order 3 is ITUB4")

            testApplication {
                application { installRoutes(OrdersController(service)) }

                val response = client.put("/orders/3/match") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"trade_id": 9}""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                    "invalid-order-match"
            }
        }
    }
})

private fun orderList() = OrderList(
    period = YearMonth.parse("2026-10"),
    orders = listOf(order()),
    transfers = listOf(transfer()),
    unplannedTrades = listOf(
        UnplannedTrade(
            id = 2,
            ticker = "CURY3",
            side = TradeSide.SELL,
            quantity = BigDecimal("40"),
        ),
    ),
)

private fun order(matchedTradeId: Int? = 1) = SuggestedOrder(
    id = 3,
    ticker = "ITUB4",
    side = OrderSide.BUY,
    quantity = BigDecimal("70"),
    suggestedPrice = BigDecimal("10"),
    executedQuantity = BigDecimal("70"),
    status = SuggestedOrderStatus.EXECUTED,
    matchedTradeId = matchedTradeId,
)

private fun transfer(status: TransferStatus = TransferStatus.PENDING) = TransferProposal(
    id = 7,
    ticker = "ITUB4",
    fromStrategyId = 2,
    fromStrategy = "Small",
    toStrategyId = 1,
    toStrategy = "Top",
    quantity = BigDecimal("10"),
    amount = BigDecimal("100"),
    autoApproved = false,
    status = status,
)
