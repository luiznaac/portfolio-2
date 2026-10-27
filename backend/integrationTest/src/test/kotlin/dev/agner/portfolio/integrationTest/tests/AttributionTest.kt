package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.getRequest
import dev.agner.portfolio.integrationTest.config.postRequest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import io.mockk.every
import java.math.BigDecimal
import java.time.Instant

// The attribution ledger's upsert, ceiling and delete only hold if they survive a real MySQL
// round-trip: the service and controller tests mock the repository away.
@IntegrationTest
class AttributionTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "attribution slices persist, replace and delete against the position's custody" {
        val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
        postRequest<Map<String, Any>> {
            path = "/trades"
            body = manualBody(institution.id)
        }
        val dividends = getBean<StrategyService>()
            .create(StrategyCreation("Top Dividendos", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN))
        val acoes = getBean<StrategyService>()
            .create(StrategyCreation("Top Ações", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN))

        val first = attribute(dividends.id, "60")
        (first["attributed_quantity"] as Number).toInt() shouldBe 60
        (first["unattributed_quantity"] as Number).toInt() shouldBe 40

        val second = attribute(acoes.id, "25")
        (second["attributed_quantity"] as Number).toInt() shouldBe 85
        (second["unattributed_quantity"] as Number).toInt() shouldBe 15

        val read = readAttribution()
        (read["slices"] as List<*>).size shouldBe 2

        val response = getBean<HttpClient>().delete(
            "http://localhost:8080/positions/PETR4/attribution/${dividends.id}",
        )
        response.status shouldBe HttpStatusCode.OK
        val after = response.body<Map<String, Any>>()
        (after["attributed_quantity"] as Number).toInt() shouldBe 25
        (after["unattributed_quantity"] as Number).toInt() shouldBe 75
        (after["slices"] as List<*>).size shouldBe 1
    }
})

private suspend fun attribute(strategyId: Int, quantity: String) = postRequest<Map<String, Any>> {
    path = "/positions/PETR4/attribution"
    body = mapOf("strategy_id" to strategyId, "quantity" to BigDecimal(quantity))
}

private suspend fun readAttribution() = getRequest<Map<String, Any>> {
    path = "/positions/PETR4/attribution"
}

private fun manualBody(institutionId: Int) = mapOf(
    "ticker" to "PETR4",
    "side" to "BUY",
    "date" to "2026-10-14",
    "venue" to "VISTA",
    "quantity" to BigDecimal("100"),
    "unit_price" to BigDecimal("10"),
    "institution_id" to institutionId,
)
