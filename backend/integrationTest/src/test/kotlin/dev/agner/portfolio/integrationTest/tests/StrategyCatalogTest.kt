package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.appBaseUrl
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.mockk.every
import kotlinx.datetime.YearMonth
import java.math.BigDecimal
import java.time.Instant

// The catalog read over a real MySQL round-trip: `GET /strategies` carries each strategy's latest
// edition as the additive `latest_edition` row, so the comparison list never fans out one editions
// call per strategy. The latest is the highest reference month, staged or active; a strategy with
// no edition omits the row entirely.
@IntegrationTest
class StrategyCatalogTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-11-12T10:00:00Z")
    }

    "carries each strategy's latest edition without the edition's entries" {
        val strategies = getBean<StrategyService>()
        val withEditions = strategies.create(
            StrategyCreation("Top Dividendos", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN),
        )
        strategies.create(StrategyCreation("Small Caps", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN))
        strategies.create(StrategyCreation("Legado", AssetClass.ACOES, StrategyKind.MANUAL))

        strategies.importEdition(
            withEditions.id,
            ParsedStrategyReport(
                referenceMonth = YearMonth.parse("2026-10"),
                parser = "xp",
                entries = listOf(EditionEntry("PETR4", BigDecimal("100"))),
            ),
        )
        strategies.importEdition(
            withEditions.id,
            ParsedStrategyReport(
                referenceMonth = YearMonth.parse("2026-11"),
                parser = "xp",
                entries = listOf(EditionEntry("VALE3", BigDecimal("100"))),
            ),
        )

        val byName = catalog().associateBy { it.get("name").asText() }

        val latest = byName.getValue("Top Dividendos").get("latest_edition")
        latest.get("reference_month").asText() shouldBe "2026-11"
        latest.get("status").asText() shouldBe "STAGED"
        latest.has("entries") shouldBe false
        byName.getValue("Small Caps").has("latest_edition") shouldBe false
        byName.getValue("Legado").has("latest_edition") shouldBe false
    }
})

private suspend fun catalog(): List<JsonNode> =
    ObjectMapper().readTree(
        getBean<HttpClient>().get("${appBaseUrl()}/strategies").bodyAsText(),
    ).toList()
