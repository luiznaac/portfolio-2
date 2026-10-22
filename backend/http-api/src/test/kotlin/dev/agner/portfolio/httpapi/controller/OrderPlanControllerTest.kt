package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.classTarget
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.order.FundingCalculator
import dev.agner.portfolio.usecase.order.FundingContext
import dev.agner.portfolio.usecase.order.OrderPlanCalculator
import dev.agner.portfolio.usecase.order.OrderPlanContext
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.AttributionMove
import dev.agner.portfolio.usecase.order.model.AttributionSide
import dev.agner.portfolio.usecase.order.model.AttributionTransfer
import dev.agner.portfolio.usecase.order.model.FixedIncomeOrder
import dev.agner.portfolio.usecase.order.model.FixedIncomeOrderPlan
import dev.agner.portfolio.usecase.order.model.FixedIncomeOrderSide
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.order.model.OutsideStrategyHolding
import dev.agner.portfolio.usecase.order.model.ProRataSummary
import dev.agner.portfolio.usecase.order.model.SubClassOrderPlan
import dev.agner.portfolio.usecase.order.model.tickerSlices
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
import io.mockk.mockk
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class OrderPlanControllerTest : DescribeSpec({

    describe("reading the current order plan") {

        it("serves the ideal chain with snake_case keys and no candidates by default") {
            val service = mockk<OrderPlanService>()
            coEvery { service.computePlan(OrderPlanPhase.IDEALS) } returns plan(OrderPlanPhase.IDEALS)

            testApplication {
                application { installRoutes(OrderPlanController(service)) }

                val response = client.get("/month/current/orders")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("period").asText() shouldBe "2026-10"
                body.get("phase").asText() shouldBe "IDEALS"
                body.get("investable_capital").asInt() shouldBe 33075

                val ticker = body.get("classes").get(0).get("strategies").get(0).get("tickers").get(0)
                ticker.get("ticker").asText() shouldBe "ITUB4"
                ticker.get("target_quantity").asText() shouldBe "38"
                body.get("candidates").size() shouldBe 0
            }
        }

        it("serves BUY candidates with their deficit amounts at the candidates phase") {
            val service = mockk<OrderPlanService>()
            coEvery { service.computePlan(OrderPlanPhase.CANDIDATES) } returns plan(OrderPlanPhase.CANDIDATES)

            testApplication {
                application { installRoutes(OrderPlanController(service)) }

                val response = client.get("/month/current/orders?phase=candidates")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("phase").asText() shouldBe "CANDIDATES"

                val candidate = body.get("candidates").get(0)
                candidate.get("ticker").asText() shouldBe "ITUB4"
                candidate.get("side").asText() shouldBe "BUY"
                candidate.get("quantity").asText() shouldBe "10"
                candidate.get("deficit").asText() shouldBe "481.82"
            }
        }

        it("serves the funding summary and the funding SELLs at the funding phase") {
            val service = mockk<OrderPlanService>()
            coEvery { service.computePlan(OrderPlanPhase.FUNDING) } returns fundingPlan()

            testApplication {
                application { installRoutes(OrderPlanController(service)) }

                val response = client.get("/month/current/orders?phase=funding")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("phase").asText() shouldBe "FUNDING"

                val sell = body.get("candidates").single { it.get("side").asText() == "SELL" }
                sell.get("ticker").asText() shouldBe "ITUB4"
                sell.get("quantity").decimalValue().compareTo(BigDecimal("20")) shouldBe 0
                sell.get("funding_source").asText() shouldBe "SURPLUS"

                val funding = body.get("funding")
                funding.get("buy_amount").decimalValue().compareTo(BigDecimal("5000")) shouldBe 0
                funding.get("sell_amount").decimalValue().compareTo(BigDecimal("1000")) shouldBe 0
                funding.get("unfunded_amount").decimalValue().compareTo(BigDecimal.ZERO) shouldBe 0
                funding.get("neutro_deficit").decimalValue().compareTo(BigDecimal.ZERO) shouldBe 0
            }
        }

        it("serves the complete plan at the full phase") {
            val service = mockk<OrderPlanService>()
            coEvery { service.computePlan(OrderPlanPhase.FULL) } returns fullPlan()

            testApplication {
                application { installRoutes(OrderPlanController(service)) }

                val response = client.get("/month/current/orders?phase=full")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("phase").asText() shouldBe "FULL"

                body.get("pro_rata").get("factor").decimalValue().compareTo(BigDecimal("0.9")) shouldBe 0
                body.get("outside_strategy").get(0).get("ticker").asText() shouldBe "MXRF11"
                body.get("outside_strategy").get(0).get("suggested_exit_quantity").asInt() shouldBe 25
                body.get("attribution").get(0).get("side").asText() shouldBe "ALLOCATE"
                body.get("transfers").get(0).get("from_strategy").asText() shouldBe "Dividendos Plus"

                val fixedIncome = body.get("classes").single { it.get("asset_class").asText() == "RENDA_FIXA" }
                val order = fixedIncome.get("sub_classes").get(0).get("orders").get(0)
                order.get("side").asText() shouldBe "CONTRIBUTE"
                order.get("amount").decimalValue().compareTo(BigDecimal("100")) shouldBe 0
            }
        }

        it("rejects an unknown phase with the canonical error body") {
            val service = mockk<OrderPlanService>()

            testApplication {
                application { installRoutes(OrderPlanController(service)) }

                val response = client.get("/month/current/orders?phase=bogus")

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

private fun plan(phase: OrderPlanPhase) = OrderPlanCalculator.compute(
    OrderPlanContext(
        period = YearMonth.parse("2026-10"),
        phase = phase,
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

// The funding phase's plan, composed the way OrderPlanService composes it: the chain and candidates
// from the calculator, then the funding pass with the whole-book cash.
private fun fundingPlan(): OrderPlan {
    val positions = mapOf("ITUB4" to BigDecimal("120"))
    val prices = mapOf("VALE3" to BigDecimal("100"), "ITUB4" to BigDecimal("50"))
    val plan = OrderPlanCalculator.compute(
        OrderPlanContext(
            period = YearMonth.parse("2026-10"),
            phase = OrderPlanPhase.FUNDING,
            capital = BigDecimal("10000"),
            floor = BigDecimal("0"),
            targets = Targets(
                classes = listOf(classTarget(AssetClass.ACOES, "100")),
                strategies = listOf(strategyTarget(AssetClass.ACOES, "Top", "100")),
                subClasses = emptyList(),
                tickers = listOf(
                    tickerTarget("Top", "VALE3", "50"),
                    tickerTarget("Top", "ITUB4", "50"),
                ),
            ),
            positions = positions,
            prices = prices,
        ),
    )
    val funding = FundingCalculator.compute(
        FundingContext(
            candidates = plan.candidates,
            slices = plan.tickerSlices,
            positions = positions,
            prices = prices,
            floor = BigDecimal.ZERO,
            uninvestedBalance = BigDecimal("4000"),
        ),
    )
    return plan.copy(candidates = plan.candidates + funding.sells, funding = funding.summary)
}

// The full phase's plan: the funding plan plus the pro-rata arithmetic, the dropped-ticker attention,
// the attribution moves and transfers, and the RF leaf orders.
private fun fullPlan(): OrderPlan {
    val funded = fundingPlan()
    return funded.copy(
        phase = OrderPlanPhase.FULL,
        classes = funded.classes.map { if (it is FixedIncomeOrderPlan) fixedIncomePlan() else it },
        proRata = ProRataSummary(
            originalBuyAmount = BigDecimal("5000"),
            factor = BigDecimal("0.9"),
            buyAmount = BigDecimal("4500"),
            unfundedAmount = BigDecimal("500"),
        ),
        outsideStrategy = listOf(
            OutsideStrategyHolding(
                ticker = "MXRF11",
                quantity = BigDecimal("25"),
                price = BigDecimal("10"),
                amount = BigDecimal("250"),
                suggestedExitQuantity = BigDecimal("25"),
            ),
        ),
        attribution = listOf(
            AttributionMove(
                ticker = "ITUB4",
                strategy = "Top",
                side = AttributionSide.ALLOCATE,
                quantity = BigDecimal("249"),
            ),
        ),
        transfers = listOf(
            AttributionTransfer(
                ticker = "ITUB4",
                fromStrategy = "Dividendos Plus",
                toStrategy = "Top",
                quantity = BigDecimal.ONE,
                price = BigDecimal("50"),
                amount = BigDecimal("50"),
            ),
        ),
    )
}

private fun fixedIncomePlan(): FixedIncomeOrderPlan = FixedIncomeOrderPlan(
    target = BigDecimal("10"),
    ideal = BigDecimal("100"),
    subClasses = listOf(
        SubClassOrderPlan(
            subClass = FixedIncomeSubClass.FLOATING_RATE,
            target = BigDecimal("100"),
            ideal = BigDecimal("100"),
            actual = BigDecimal.ZERO,
            deficit = BigDecimal("100"),
            orders = listOf(
                FixedIncomeOrder(
                    subClass = FixedIncomeSubClass.FLOATING_RATE,
                    side = FixedIncomeOrderSide.CONTRIBUTE,
                    amount = BigDecimal("100"),
                ),
            ),
        ),
    ),
)
