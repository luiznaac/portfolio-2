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
import dev.agner.portfolio.usecase.corporateaction.CorporateActionService
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.import.ImportService
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.listedasset.model.GapCreation
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.order.model.TransferProposalCreation
import dev.agner.portfolio.usecase.order.model.TransferStatus
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal
import java.time.Instant

// The landing's aggregate over a real MySQL round-trip: the typed status must be composed from the
// stored month, book, imports, editions, gaps, corporate actions and quotes — and a clean book must
// raise no attention at all. A mocked composition cannot prove the queue empties on a real read.
@IntegrationTest
class PortfolioStatusTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-11-12T10:00:00Z")
    }

    "serves the status with an empty attention queue for a clean seeded book" {
        catalog("PETR4", AssetClass.ACOES)
        position("PETR4", "10")
        quote("PETR4", "2026-11-11", "48.92")
        openMonth()

        val body = status()

        body.get("as_of").asText() shouldBe "2026-11-12"
        body.get("open_month").get("period").asText() shouldBe "2026-11"
        body.has("last_close") shouldBe false
        body.get("capital").decimalValue().compareTo(BigDecimal("10000")) shouldBe 0
        body.get("invested").decimalValue().compareTo(BigDecimal("489.20")) shouldBe 0
        body.get("uninvested_balance").decimalValue().compareTo(BigDecimal("9510.80")) shouldBe 0
        body.get("month").get("open").asBoolean() shouldBe true
        body.get("month").get("steps_done").asInt() shouldBe 2
        body.get("month").get("steps_total").asInt() shouldBe 6
        body.get("month").get("next_action").get("kind").asText() shouldBe "REFRESH_PLAN"
        body.get("data").get("quotes_as_of").asText() shouldBe "2026-11-11"
        body.get("data").get("open_gaps").asInt() shouldBe 0
        body.get("data").has("last_snapshot") shouldBe false
        body.get("attention").size() shouldBe 0
    }

    "raises every actionable finding from its seeded state" {
        val month = openMonth()
        catalog("PETR4", AssetClass.ACOES)
        catalogUnclassified("ZZZZ9")
        position("PETR4", "10")
        position("ZZZZ9", "5")
        // Well before the clock's date: stale by the five-day rule.
        quote("PETR4", "2026-10-01", "48.92")

        val strategies = getBean<StrategyService>()
        val smallCaps = strategies.create(
            StrategyCreation("Small Caps", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN),
        )
        val topDividends = strategies.create(
            StrategyCreation("Top Dividendos", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN),
        )
        getBean<ITransferProposalRepository>().save(
            month.month.id,
            TransferProposalCreation(
                ticker = "ITUB4",
                fromStrategyId = smallCaps.id,
                toStrategyId = topDividends.id,
                quantity = BigDecimal("50"),
                amount = BigDecimal("500"),
                autoApproved = false,
                status = TransferStatus.PENDING,
            ),
        )
        getBean<ImportService>().stage(ImportSource.B3_TRADES, "inbox.xlsx", "inbox-hash", emptyList())

        strategies.importEdition(
            topDividends.id,
            ParsedStrategyReport(
                referenceMonth = YearMonth.parse("2026-11"),
                parser = "xp",
                entries = listOf(EditionEntry("PETR4", BigDecimal("100"))),
            ),
        )
        getBean<IGapRepository>().save(
            GapCreation.MissingBuy(
                ticker = "VALE3",
                date = LocalDate.parse("2026-10-01"),
                quantity = BigDecimal("5"),
                unitPrice = BigDecimal("61.10"),
            ),
        )
        getBean<CorporateActionService>().importEvents(
            listOf(
                CorporateActionCreation(
                    ticker = "VALE3",
                    kind = CorporateActionKind.RIGHTS_ISSUE,
                    exDate = LocalDate.parse("2026-10-20"),
                ),
            ),
        )

        val body = status()

        body.get("month").get("pending_transfers").asInt() shouldBe 1
        body.get("data").get("open_gaps").asInt() shouldBe 1
        body.get("data").get("stale_quotes").get(0).get("ticker").asText() shouldBe "PETR4"
        body.get("attention").map { it.get("kind").asText() } shouldBe listOf(
            "PENDING_TRANSFER",
            "STAGED_IMPORT",
            "STAGED_EDITION",
            "CORPORATE_ACTION_REVIEW",
            "UNCLASSIFIED_TICKER",
            "OPEN_GAP",
            "STALE_QUOTE",
        )
        body.get("attention").map { it.get("severity").asText() } shouldBe listOf(
            "ACTION",
            "ACTION",
            "ACTION",
            "ACTION",
            "ACTION",
            "ACTION",
            "WATCH",
        )
        body.get("attention").map { it.get("object").asText() } shouldBe listOf(
            "MONTH",
            "IMPORT",
            "STRATEGY",
            "CORPORATE_ACTION",
            "ASSET",
            "ASSET",
            "ASSET",
        )
        body.get("attention").get(0).get("ref").get("type").asText() shouldBe "TRANSFER"
        body.get("attention").get(0).get("params").get("quantity").decimalValue()
            .compareTo(BigDecimal("50")) shouldBe 0
        body.get("attention").get(5).get("ref").get("type").asText() shouldBe "GAP"
        body.get("attention").get(6).get("params").get("days_old").asInt() shouldBe 42
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
                lastSeen = LocalDate.parse("2026-11-12"),
            ),
        ),
    )
}

private suspend fun catalogUnclassified(ticker: String) {
    getBean<ICatalogRepository>().upsertAll(
        listOf(
            CatalogEntry(
                ticker = ticker,
                shortName = ticker,
                bdiType = "??",
                isin = null,
                assetKind = AssetKind.UNKNOWN,
                assetClass = null,
                firstSeen = LocalDate.parse("2026-01-01"),
                lastSeen = LocalDate.parse("2026-11-12"),
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

private suspend fun openMonth(): dev.agner.portfolio.usecase.portfolio.model.CurrentMonth {
    val months = getBean<MonthService>()
    val current = months.open(
        MonthCreation(
            period = YearMonth.parse("2026-11"),
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
    return current
}

private suspend fun status(): JsonNode =
    ObjectMapper().readTree(
        getBean<HttpClient>().get("http://localhost:8080/portfolio/status").bodyAsText(),
    )
