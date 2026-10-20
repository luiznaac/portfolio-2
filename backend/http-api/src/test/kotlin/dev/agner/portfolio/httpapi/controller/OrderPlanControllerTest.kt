package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.classTarget
import dev.agner.portfolio.usecase.order.OrderPlanCalculator
import dev.agner.portfolio.usecase.order.OrderPlanContext
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.strategyTarget
import dev.agner.portfolio.usecase.tickerTarget
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class OrderPlanControllerTest : DescribeSpec({

    describe("reading the current order plan") {

        it("serves the ideal plan with snake_case keys and its leaf candidate") {
            val service = mockk<OrderPlanService>()
            coEvery { service.computePlan(OrderPlanPhase.IDEALS) } returns plan

            testApplication {
                application { installRoutes(OrderPlanController(service)) }

                val response = client.get("/month/current/orders?phase=ideals")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("period").asText() shouldBe "2026-10"
                body.get("phase").asText() shouldBe "IDEALS"
                body.get("investable_capital").asInt() shouldBe 33075

                val ticker = body.get("classes").get(0).get("strategies").get(0).get("tickers").get(0)
                ticker.get("ticker").asText() shouldBe "ITUB4"
                ticker.get("target_quantity").asText() shouldBe "38"

                val candidate = body.get("candidates").get(0)
                candidate.get("side").asText() shouldBe "BUY"
                candidate.get("quantity").asText() shouldBe "10"
            }
        }

        it("defaults to the ideals phase when the query omits it") {
            val service = mockk<OrderPlanService>()
            coEvery { service.computePlan(OrderPlanPhase.IDEALS) } returns plan

            testApplication {
                application { installRoutes(OrderPlanController(service)) }

                client.get("/month/current/orders").status shouldBe HttpStatusCode.OK
            }

            coVerify(exactly = 1) { service.computePlan(OrderPlanPhase.IDEALS) }
        }

        it("rejects an unknown phase with the canonical error body") {
            val service = mockk<OrderPlanService>()

            testApplication {
                application { installRoutes(OrderPlanController(service)) }

                val response = client.get("/month/current/orders?phase=funding")

                response.status shouldBe HttpStatusCode.BadRequest
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("error").asText() shouldBe "invalid-order-plan-phase"
            }
        }

        it("404s when no month is open") {
            val service = mockk<OrderPlanService>()
            coEvery { service.computePlan(OrderPlanPhase.IDEALS) } returns null

            testApplication {
                application { installRoutes(OrderPlanController(service)) }

                client.get("/month/current/orders?phase=ideals").status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})

private val plan = OrderPlanCalculator.compute(
    OrderPlanContext(
        period = YearMonth.parse("2026-10"),
        phase = OrderPlanPhase.IDEALS,
        capital = BigDecimal("49075"),
        floor = BigDecimal("16000"),
        targets = Targets(
            classes = listOf(classTarget(AssetClass.ACOES, "100")),
            strategies = listOf(
                strategyTarget(AssetClass.ACOES, "Top", "50"),
                strategyTarget(AssetClass.ACOES, "Dividendos", "25"),
                strategyTarget(AssetClass.ACOES, "Dividendos Plus", "25"),
            ),
            subClasses = emptyList(),
            tickers = listOf(
                tickerTarget("Top", "ITUB4", "10"),
                tickerTarget("Dividendos", "ITUB4", "15"),
                tickerTarget("Dividendos Plus", "ITUB4", "10"),
            ),
        ),
        positions = mapOf("ITUB4" to BigDecimal("76")),
        prices = mapOf("ITUB4" to BigDecimal("42.62")),
    ),
)
