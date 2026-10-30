package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.mockk.every
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal
import java.time.Instant

// The valuation read over a real MySQL round-trip: the allocation leaf's value must be the stored
// close times the quantity, an unpriced ticker must stay unknown (never R$ 0,00), and an old close
// must survive with its date and stale flag.
@IntegrationTest
class AllocationValuationTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "values each leaf at quantity × latest close and excludes an unpriced ticker from the total" {
        catalog("PETR4", AssetClass.ACOES)
        catalog("VALE3", AssetClass.ACOES)
        position("PETR4", "10")
        position("VALE3", "5")
        quote("PETR4", "2026-10-13", "48.92")
        openMonth()

        val body = allocation()

        body.get("actual").decimalValue().compareTo(BigDecimal("489.20")) shouldBe 0
        body.get("partial").asBoolean() shouldBe true
        body.get("unpriced").size() shouldBe 1
        body.get("unpriced").get(0).get("ticker").asText() shouldBe "VALE3"
        body.get("unpriced").get(0).get("quantity").decimalValue()
            .compareTo(BigDecimal("5")) shouldBe 0

        val outside = body.get("classes").get(0).get("outside")
        val petr = outside.get("tickers").first { it.get("ticker").asText() == "PETR4" }
        petr.get("actual").decimalValue().compareTo(BigDecimal("489.20")) shouldBe 0
        petr.get("quantity").decimalValue().compareTo(BigDecimal("10")) shouldBe 0

        val vale = outside.get("tickers").first { it.get("ticker").asText() == "VALE3" }
        vale.has("actual") shouldBe false
    }

    "keeps a close older than five days in the value with its date and flags negative custody" {
        catalog("PETR4", AssetClass.ACOES)
        catalog("BCFF11", AssetClass.ACOES)
        position("PETR4", "10")
        position("BCFF11", "-10")
        // The clock is 2026-10-14, so this close is seven calendar days old.
        quote("PETR4", "2026-10-07", "48.92")
        quote("BCFF11", "2026-10-13", "8.00")
        openMonth()

        val body = allocation()

        body.get("partial").asBoolean() shouldBe false
        body.get("actual").decimalValue().compareTo(BigDecimal("409.20")) shouldBe 0

        val petr = body.get("classes").get(0).get("outside").get("tickers")
            .first { it.get("ticker").asText() == "PETR4" }
        petr.get("stale").asBoolean() shouldBe true
        petr.get("quote_date").asText() shouldBe "2026-10-07"
        petr.get("actual").decimalValue().compareTo(BigDecimal("489.20")) shouldBe 0

        body.get("negative").size() shouldBe 1
        body.get("negative").get(0).get("ticker").asText() shouldBe "BCFF11"
        body.get("negative").get(0).get("quantity").decimalValue()
            .compareTo(BigDecimal("-10")) shouldBe 0
    }
})

private suspend fun catalog(ticker: String, assetClass: AssetClass) {
    getBean<ICatalogRepository>().upsertAll(
        listOf(
            CatalogEntry(
                ticker = ticker,
                shortName = ticker,
                bdiType = "02",
                isin = null,
                assetKind = AssetKind.STOCK,
                assetClass = assetClass,
                firstSeen = LocalDate.parse("2026-01-01"),
                lastSeen = LocalDate.parse("2026-10-14"),
            ),
        ),
    )
}

private suspend fun position(ticker: String, quantity: String) {
    getBean<IPositionRepository>().save(
        Position(ticker = ticker, quantity = BigDecimal(quantity), averagePrice = BigDecimal("10")),
    )
}

private suspend fun quote(ticker: String, date: String, close: String) {
    getBean<IQuoteRepository>().upsertAll(
        listOf(
            Quote(
                ticker = ticker,
                date = LocalDate.parse(date),
                close = BigDecimal(close),
                source = QuoteSource.COTAHIST,
            ),
        ),
    )
}

private suspend fun openMonth() {
    val months = getBean<MonthService>()
    months.open(
        MonthCreation(
            period = YearMonth.parse("2026-10"),
            capital = BigDecimal("10000"),
            floor = BigDecimal.ZERO,
        ),
    )
    months.replaceCurrentTargets(
        Targets(
            classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("100"))),
            strategies = emptyList(),
            subClasses = emptyList(),
            tickers = emptyList(),
        ),
    )
}

private suspend fun allocation(): JsonNode =
    ObjectMapper().readTree(
        getBean<HttpClient>().get("http://localhost:8080/month/current/allocation").bodyAsText(),
    )
