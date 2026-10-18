package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.portfolio.AllocationCalculator
import dev.agner.portfolio.usecase.portfolio.AllocationService
import dev.agner.portfolio.usecase.portfolio.ListedHolding
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerTarget
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
            }
        }

        it("404s when no month is open") {
            val service = mockk<AllocationService>()
            coEvery { service.fetchCurrent() } returns null

            testApplication {
                application { installRoutes(AllocationController(service)) }

                client.get("/month/current/allocation").status shouldBe HttpStatusCode.NotFound
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
    ),
    fixedIncome = emptyList(),
)
