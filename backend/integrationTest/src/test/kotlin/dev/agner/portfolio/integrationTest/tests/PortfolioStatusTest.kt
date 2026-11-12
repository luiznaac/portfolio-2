package dev.agner.portfolio.integrationTest.tests

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
import dev.agner.portfolio.usecase.portfolio.PortfolioStatusService
import dev.agner.portfolio.usecase.portfolio.model.AttentionKind
import dev.agner.portfolio.usecase.portfolio.model.AttentionObject
import dev.agner.portfolio.usecase.portfolio.model.AttentionSeverity
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.MonthActionKind
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.StatusRefType
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
import io.mockk.every
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal
import java.time.Instant

// The landing's aggregate over a real MySQL round-trip: the typed status must be composed from the
// stored month, book, imports, editions, gaps, corporate actions and quotes — and a clean book must
// raise no attention at all. The service seam is deliberate: the test context's Ktor server is
// stopped between methods, so a direct service read is the deterministic way to assert the aggregate.
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

        val status = status()

        status.asOf shouldBe LocalDate.parse("2026-11-12")
        status.openMonth?.period shouldBe YearMonth.parse("2026-11")
        status.lastClose shouldBe null
        status.capital.compareTo(BigDecimal("10000")) shouldBe 0
        status.invested.compareTo(BigDecimal("489.20")) shouldBe 0
        status.uninvestedBalance!!.compareTo(BigDecimal("9510.80")) shouldBe 0
        status.month.open shouldBe true
        status.month.stepsDone shouldBe 2
        status.month.stepsTotal shouldBe 6
        status.month.nextAction?.kind shouldBe MonthActionKind.REFRESH_PLAN
        status.data.quotesAsOf shouldBe LocalDate.parse("2026-11-11")
        status.data.openGaps shouldBe 0
        status.data.lastSnapshot shouldBe null
        status.attention shouldBe emptyList()
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

        val status = status()

        status.month.pendingTransfers shouldBe 1
        status.data.openGaps shouldBe 1
        status.data.staleQuotes.single().ticker shouldBe "PETR4"
        status.attention.map { it.kind } shouldBe listOf(
            AttentionKind.PENDING_TRANSFER,
            AttentionKind.STAGED_IMPORT,
            AttentionKind.STAGED_EDITION,
            AttentionKind.CORPORATE_ACTION_REVIEW,
            AttentionKind.UNCLASSIFIED_TICKER,
            AttentionKind.OPEN_GAP,
            AttentionKind.STALE_QUOTE,
        )
        status.attention.map { it.severity } shouldBe listOf(
            AttentionSeverity.ACTION,
            AttentionSeverity.ACTION,
            AttentionSeverity.ACTION,
            AttentionSeverity.ACTION,
            AttentionSeverity.ACTION,
            AttentionSeverity.ACTION,
            AttentionSeverity.WATCH,
        )
        status.attention.map { it.`object` } shouldBe listOf(
            AttentionObject.MONTH,
            AttentionObject.IMPORT,
            AttentionObject.STRATEGY,
            AttentionObject.CORPORATE_ACTION,
            AttentionObject.ASSET,
            AttentionObject.ASSET,
            AttentionObject.ASSET,
        )
        status.attention[0].ref.type shouldBe StatusRefType.TRANSFER
        status.attention[0].params.getValue("quantity").compareTo(BigDecimal("50")) shouldBe 0
        status.attention[5].ref.type shouldBe StatusRefType.GAP
        status.attention[6].params.getValue("days_old").toInt() shouldBe 42
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

private suspend fun openMonth(): CurrentMonth {
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

private suspend fun status() = getBean<PortfolioStatusService>().fetch()
