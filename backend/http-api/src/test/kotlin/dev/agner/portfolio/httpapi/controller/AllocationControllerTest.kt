package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.portfolio.AllocationCalculator
import dev.agner.portfolio.usecase.portfolio.AllocationService
import dev.agner.portfolio.usecase.portfolio.ListedHolding
import dev.agner.portfolio.usecase.portfolio.ManualStrategyHolding
import dev.agner.portfolio.usecase.portfolio.model.ActualTickerAllocation
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.HeldTicker
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerTarget
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
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

class AllocationControllerTest : DescribeSpec({

    describe("reading the current allocation") {

        it("serves the tree with snake_case keys and the chain's rungs") {
            val service = mockk<AllocationService>()
            coEvery { service.fetchCurrent() } returns allocation

            testApplication {
                application { installRoutes(AllocationController(service)) }

                val response = client.get("/month/current/allocation")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("period").asText() shouldBe "2026-09"
                body.get("drift").isNumber shouldBe true
                body.get("unattributed").isNumber shouldBe true

                val acoes = body.get("classes").get(0)
                acoes.get("asset_class").asText() shouldBe "ACOES"
                acoes.get("target").isNumber shouldBe true
                acoes.get("strategies").get(0).get("strategy").asText() shouldBe "Top Dividendos"
                acoes.get("strategies").get(0).get("tickers").get(0).get("ticker").asText() shouldBe "VALE3"
                acoes.get("strategies").get(0).get("tickers").get(0).get("quantity").decimalValue()
                    .compareTo(BigDecimal("60")) shouldBe 0
                body.get("partial").asBoolean() shouldBe false
                body.get("unpriced").size() shouldBe 0
                body.get("negative").size() shouldBe 0
                acoes.get("manual").get("strategy").asText() shouldBe "Legado Ações"
                acoes.get("manual").has("target") shouldBe false
                acoes.get("outside").get("actual").decimalValue()
                    .compareTo(BigDecimal("400.00")) shouldBe 0
                body.get("unclassified").get("actual").decimalValue()
                    .compareTo(BigDecimal.ZERO) shouldBe 0
            }
        }

        it("omits an unpriced leaf's actual, lists it as unpriced and marks the read partial") {
            val service = mockk<AllocationService>()
            val unpriced = allocation.copy(
                partial = true,
                unpriced = listOf(HeldTicker("ZZZZ9", BigDecimal("5"))),
                classes = allocation.classes.map { rung ->
                    rung.copy(
                        outside = rung.outside.copy(
                            tickers = rung.outside.tickers + ActualTickerAllocation(
                                ticker = "ZZZZ9",
                                actual = null,
                                quantity = BigDecimal("5"),
                            ),
                        ),
                    )
                },
            )
            coEvery { service.fetchCurrent() } returns unpriced

            testApplication {
                application { installRoutes(AllocationController(service)) }

                val body = ObjectMapper().readTree(client.get("/month/current/allocation").bodyAsText())

                body.get("partial").asBoolean() shouldBe true
                body.get("unpriced").get(0).get("ticker").asText() shouldBe "ZZZZ9"
                body.get("unpriced").get(0).get("quantity").decimalValue()
                    .compareTo(BigDecimal("5")) shouldBe 0
                val leaf = body.get("classes").get(0).get("outside").get("tickers")
                    .first { it.get("ticker").asText() == "ZZZZ9" }
                leaf.has("actual") shouldBe false
                leaf.get("quantity").decimalValue().compareTo(BigDecimal("5")) shouldBe 0
            }
        }

        it("serves an Actual-only tree with no period when no month is open") {
            val service = mockk<AllocationService>()
            coEvery { service.fetchCurrent() } returns allocation.copy(period = null)

            testApplication {
                application { installRoutes(AllocationController(service)) }

                val response = client.get("/month/current/allocation")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.has("period") shouldBe false
                body.get("actual").decimalValue().compareTo(BigDecimal("1500.00")) shouldBe 0
                body.get("classes").get(0).get("actual").decimalValue()
                    .compareTo(BigDecimal("1500.00")) shouldBe 0
            }
        }
    }
})

private val allocation = AllocationCalculator.compute(
    period = YearMonth.parse("2026-09"),
    capital = BigDecimal("100000"),
    floor = BigDecimal("20000"),
    targets = Targets(
        classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("50"))),
        strategies = listOf(StrategyTarget(AssetClass.ACOES, "Top Dividendos", BigDecimal("100"))),
        subClasses = emptyList(),
        tickers = listOf(TickerTarget("Top Dividendos", "VALE3", BigDecimal("100"))),
    ),
    listed = listOf(
        ListedHolding(
            ticker = "VALE3",
            assetClass = AssetClass.ACOES,
            quantity = BigDecimal("100"),
            price = BigDecimal("10"),
        ),
        ListedHolding(
            ticker = "ITUB4",
            assetClass = AssetClass.ACOES,
            quantity = BigDecimal("50"),
            price = BigDecimal("10"),
        ),
    ),
    fixedIncome = emptyList(),
    attributions = mapOf(
        StrategyTicker("Top Dividendos", "VALE3") to BigDecimal("60"),
        StrategyTicker("Legado Ações", "VALE3") to BigDecimal("20"),
        StrategyTicker("Legado Ações", "ITUB4") to BigDecimal("30"),
    ),
    manualStrategies = listOf(ManualStrategyHolding("Legado Ações", AssetClass.ACOES)),
)
