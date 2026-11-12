package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.appBaseUrl
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.import.ImportService
import dev.agner.portfolio.usecase.import.model.ImportDestination
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerCursor
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerFilter
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerPage
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant

// The ledger's paging and filtering live in SQL, so only a real MySQL round-trip proves the cursor
// does not repeat or skip a row and that every filter narrows the page and its total together.
@IntegrationTest
class TradeLedgerTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "the cursor pages the whole ledger newest first without repeating or skipping a row" {
        applyImport(
            listOf(
                ledgerRow("2026-10-01", "PETR4", TradeSide.BUY),
                ledgerRow("2026-10-02", "VALE3", TradeSide.SELL),
                ledgerRow("2026-10-03", "PETR4", TradeSide.SELL),
                ledgerRow("2026-10-04", "ITUB4", TradeSide.BUY),
            ),
        )
        val service = getBean<TradeService>()

        val first = service.fetchLedger(TradeLedgerFilter(limit = 2))

        first.total shouldBe 4
        first.financialTotal.compareTo(BigDecimal("400")) shouldBe 0
        first.rows.map { it.ticker } shouldBe listOf("ITUB4", "PETR4")
        first.rows.map { it.date.toString() } shouldBe listOf("2026-10-04", "2026-10-03")
        val last = first.rows.last()
        first.nextCursor shouldBe TradeLedgerCursor(last.date, last.id).encode()

        val second = service.fetchLedger(
            TradeLedgerFilter(limit = 2, after = TradeLedgerCursor.decode(first.nextCursor!!)),
        )

        second.total shouldBe 4
        second.financialTotal.compareTo(BigDecimal("400")) shouldBe 0
        second.rows.map { it.ticker } shouldBe listOf("VALE3", "PETR4")
        second.nextCursor shouldBe null
        (first.rows + second.rows).map { it.id }.distinct().size shouldBe 4
    }

    "the ticker, date window and side filters narrow the page and its total together" {
        applyImport(
            listOf(
                ledgerRow("2026-10-01", "PETR4", TradeSide.BUY),
                ledgerRow("2026-10-02", "VALE3", TradeSide.SELL),
                ledgerRow("2026-10-03", "PETR4", TradeSide.SELL),
                ledgerRow("2026-10-04", "ITUB4", TradeSide.BUY),
            ),
        )
        val service = getBean<TradeService>()

        service.fetchLedger(TradeLedgerFilter(ticker = "petr"))
            .shouldHold(listOf("PETR4", "PETR4"))
        service.fetchLedger(
            TradeLedgerFilter(from = LocalDate.parse("2026-10-02"), to = LocalDate.parse("2026-10-03")),
        ).shouldHold(listOf("PETR4", "VALE3"))
        service.fetchLedger(TradeLedgerFilter(side = TradeSide.SELL))
            .shouldHold(listOf("PETR4", "VALE3"))
    }

    "the HTTP read returns the page with its next cursor and filtered total" {
        applyImport(
            listOf(
                ledgerRow("2026-10-01", "PETR4", TradeSide.BUY),
                ledgerRow("2026-10-02", "VALE3", TradeSide.SELL),
                ledgerRow("2026-10-03", "PETR4", TradeSide.SELL),
            ),
        )

        val body = ObjectMapper().readTree(
            getBean<HttpClient>().get("${appBaseUrl()}/trades/ledger?ticker=PETR&side=SELL")
                .bodyAsText(),
        )

        body.get("rows").size() shouldBe 1
        body.get("rows").get(0).get("ticker").asText() shouldBe "PETR4"
        body.get("rows").get(0).get("side").asText() shouldBe "SELL"
        body.get("total").asInt() shouldBe 1
        body.get("financial_total").decimalValue().compareTo(BigDecimal("100")) shouldBe 0
        body.get("next_cursor").isNull shouldBe true
    }
})

// Both filters in this spec keep the four-row fixture at two 100,00 rows, so the expected page is
// stated once per filter instead of repeating the three assertions.
private fun TradeLedgerPage.shouldHold(tickers: List<String>) {
    rows.map { it.ticker } shouldBe tickers
    total shouldBe tickers.size
    financialTotal.compareTo(BigDecimal("200")) shouldBe 0
}

private suspend fun applyImport(rows: List<ImportRowPayload>) {
    val service = getBean<ImportService>()
    val import = service.stage(
        ImportSource.B3_TRADES,
        "ledger.xlsx",
        "ledger-hash",
        rows,
        ImportDestination(ImportDestinationObject.TRADES),
    ).record
    service.review(import.id)
    service.apply(import.id)
}

private fun ledgerRow(
    date: String,
    ticker: String,
    side: TradeSide,
) = ImportRowPayload(
    date = LocalDate.parse(date),
    side = side,
    venue = "VISTA",
    ticker = ticker,
    quantity = BigDecimal("10"),
    unitPrice = BigDecimal("10"),
    institutionName = "XP INVESTIMENTOS",
)
