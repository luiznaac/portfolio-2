package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.RequestMethod
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.HttpMockService
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.appBaseUrl
import dev.agner.portfolio.integrationTest.helpers.b3SplitFixture
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.integrationTest.helpers.ricoHistoryFixture
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.io.File
import java.math.BigDecimal
import java.time.Instant

// The 2018–2021 end-to-end scenario over the real stack: the owner's synthetic Rico notes enter as
// one import, the two descriptions no alias covers are confirmed in the review, a B3 Movimentação
// split lands, and the one-off brapi sync fills the rename, the other split and the cash income.
// Positions, tax and performance are then asserted over the whole window — and a second run proves
// the sync is a no-op while the B3 event keeps the natural key brapi restates.
@IntegrationTest
class History2018To2021Test : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-11-17T10:00:00Z")
        seedCatalog()
        stubProviders()
    }

    "the imported notes and the brapi history resolve into one book" {
        val report = seedHistory()

        report.get("applied").asInt() shouldBe 2
        report.get("parked").asInt() shouldBe 0
        report.get("income").asInt() shouldBe 2
        report.get("fetched").asInt() shouldBe 5
        report.get("ticker_count").asInt() shouldBe 6
        report.get("window_from").asText() shouldBe "2018-03-05"

        val positions = positions().associateBy { it.get("ticker").asText() }
        positions.keys shouldBe setOf("B3SA3", "INBR32", "MXRF11", "PETR4", "RBRP12", "VALE3")
        positionOf(positions, "PETR4", quantity = "200", averagePrice = "10")
        positionOf(positions, "VALE3", quantity = "100", averagePrice = "15")
        positionOf(positions, "INBR32", quantity = "110", averagePrice = "9.090909")
        positionOf(positions, "MXRF11", quantity = "100", averagePrice = "10")
        positionOf(positions, "B3SA3", quantity = "100", averagePrice = "12")
        positionOf(positions, "RBRP12", quantity = "0", averagePrice = "0")

        val actions = corporateActions()
        actions.single { it.get("ticker").asText() == "PETR4" }.let { split ->
            split.get("kind").asText() shouldBe "SPLIT"
            split.get("source").asText() shouldBe "BRAPI"
            split.get("ratio").decimalValue().compareTo(BigDecimal("2")) shouldBe 0
        }
        actions.single { it.get("ticker").asText() == "VALE3" }.let { split ->
            split.get("kind").asText() shouldBe "SPLIT"
            split.get("source").asText() shouldBe "B3_MOVEMENTS"
            split.get("ratio").decimalValue().compareTo(BigDecimal("2")) shouldBe 0
        }
        actions.single { it.get("kind").asText() == "TICKER_CHANGE" }.let { rename ->
            rename.get("source").asText() shouldBe "BRAPI"
            rename.get("target_ticker").asText() shouldBe "INBR32"
            rename.get("ratio").decimalValue().compareTo(BigDecimal("1.1")) shouldBe 0
        }
        actions.single { it.get("kind").asText() == "RIGHTS_ALLOTMENT" }.let { allotment ->
            allotment.get("source").asText() shouldBe "DERIVED"
            allotment.get("granted_quantity").decimalValue().compareTo(BigDecimal("10")) shouldBe 0
        }

        // Every listed-asset read canonicalizes: the ledger row the note stored under the old code
        // answers a read under the current one.
        val ledger = json(getBean<HttpClient>().get("${appBaseUrl()}/trades/ledger?ticker=INBR32"))
        ledger.get("rows").size() shouldBe 1
        ledger.get("rows").get(0).get("ticker").asText() shouldBe "INBR32"
        ledger.get("rows").get(0).get("date").asText() shouldBe "2018-03-05"

        val income = income().associateBy { it.get("ticker").asText() }
        income.keys shouldBe setOf("PETR4", "MXRF11")
        income.getValue("PETR4").get("gross_amount").decimalValue().compareTo(BigDecimal("100")) shouldBe 0
        income.getValue("MXRF11").get("gross_amount").decimalValue().compareTo(BigDecimal("10")) shouldBe 0
        income.values.map { it.get("provenance").asText() }.toSet() shouldBe setOf("BRAPI")
    }

    "the rights sale carries the 15% DARF 6015 row and the split the right quantity and basis" {
        seedHistory()

        val rightsMonth = tax("2019-05")
        val rights = rightsMonth.get("realized").single { it.get("kind").asText() == "RIGHTS" }
        rights.get("gross_sales").decimalValue().compareTo(BigDecimal("882.70")) shouldBe 0
        rights.get("result").decimalValue().compareTo(BigDecimal("882.70")) shouldBe 0
        rightsMonth.get("meter").get("gross_sales").decimalValue().compareTo(BigDecimal.ZERO) shouldBe 0
        rightsMonth.get("rights").get("rate").decimalValue().compareTo(BigDecimal("15.00")) shouldBe 0
        rightsMonth.get("rights").get("taxable_gain").decimalValue().compareTo(BigDecimal("882.70")) shouldBe 0
        rightsMonth.get("rights").get("tax").decimalValue().compareTo(BigDecimal("132.40")) shouldBe 0
        rightsMonth.get("darf_due_date").asText() shouldBe "2019-06-28"

        val inbr = rightsMonth.get("average_prices").single { it.get("ticker").asText() == "INBR32" }
        inbr.get("quantity").decimalValue().compareTo(BigDecimal("100")) shouldBe 0
        inbr.get("average_price").decimalValue().compareTo(BigDecimal("10")) shouldBe 0

        val splitMonth = tax("2020-01")
        val petr4 = splitMonth.get("average_prices").single { it.get("ticker").asText() == "PETR4" }
        petr4.get("quantity").decimalValue().compareTo(BigDecimal("200")) shouldBe 0
        petr4.get("average_price").decimalValue().compareTo(BigDecimal("10")) shouldBe 0
    }

    "cash income nets out of the period's returns across the 2018-2021 window" {
        seedHistory()
        seedQuotes()

        val months = series().get("months")
        val window = months.map { it.get("period").asText() }.takeWhile { it <= "2021-12" }
        window.first() shouldBe "2018-03"
        window.last() shouldBe "2021-12"
        window.size shouldBe 46

        val march = months.single { it.get("period").asText() == "2018-03" }
        march.get("source").asText() shouldBe "computed"
        march.get("decomposition").get("value_end").decimalValue().compareTo(BigDecimal("4500")) shouldBe 0
        val firstStart = march.get("decomposition").get("value_start")
        (firstStart == null || firstStart.isNull) shouldBe true

        assertIncomeNetted(
            months.single { it.get("period").asText() == "2021-02" },
            valueStart = "5500",
            valueEnd = "5510",
            income = "10",
        )
        assertIncomeNetted(
            months.single { it.get("period").asText() == "2021-06" },
            valueStart = "5500",
            valueEnd = "5600",
            income = "100",
        )
    }

    "a second sync duplicates nothing and B3 keeps the shared natural key" {
        seedHistory()
        val actionsBefore = corporateActions()
        val incomeBefore = income()
        val positionsBefore = positions()

        val second = sync()

        second.get("applied").asInt() shouldBe 0
        second.get("parked").asInt() shouldBe 0
        second.get("income").asInt() shouldBe 0
        corporateActions() shouldBe actionsBefore
        income() shouldBe incomeBefore
        positions() shouldBe positionsBefore

        val vale = positions().single { it.get("ticker").asText() == "VALE3" }
        vale.get("quantity").decimalValue().compareTo(BigDecimal("100")) shouldBe 0
        vale.get("average_price").decimalValue().compareTo(BigDecimal("15")) shouldBe 0
        corporateActions().single { it.get("ticker").asText() == "VALE3" }
            .get("ratio").decimalValue().compareTo(BigDecimal("2")) shouldBe 0
    }
})

// The synthetic notes, the review's answers, the B3 split and the first sync, in the order the
// owner's month runs them.
private suspend fun seedHistory(): JsonNode {
    val importId = uploadRico()
    val rows = rows(importId)
    rows.size() shouldBe 6
    val unresolved = rows.filter { it.get("ticker") == null || it.get("ticker").isNull }
    for (row in unresolved) {
        confirmTicker(importId, row.get("id").asInt(), tickerFor(row.get("raw_description").asText()))
    }

    review(importId)
    val applied = json(apply(importId))
    applied.get("applied").asInt() shouldBe 6
    applied.get("rejected").asInt() shouldBe 0

    importB3Split()
    return sync()
}

private fun tickerFor(description: String): String = when (description) {
    "PETROBRAS PN N2" -> "PETR4"
    "VALE ON N1" -> "VALE3"
    else -> error("unexpected unresolved description: $description")
}

private suspend fun seedCatalog() {
    getBean<ICatalogRepository>().upsertAll(
        listOf(
            catalogEntry("PETR4", AssetKind.STOCK, AssetClass.ACOES),
            catalogEntry("VALE3", AssetKind.STOCK, AssetClass.ACOES),
            catalogEntry("BIDI11", AssetKind.STOCK, AssetClass.ACOES),
            catalogEntry("B3SA3", AssetKind.STOCK, AssetClass.ACOES),
            catalogEntry("MXRF11", AssetKind.FII, AssetClass.FIIS),
            catalogEntry("RBRP12", AssetKind.RIGHT, null),
        ),
    )
}

private fun catalogEntry(ticker: String, kind: AssetKind, assetClass: AssetClass?) = CatalogEntry(
    ticker = ticker,
    shortName = ticker,
    bdiType = if (kind == AssetKind.FII) "12" else "02",
    isin = null,
    assetKind = kind,
    assetClass = assetClass,
    firstSeen = LocalDate.parse("2018-01-02"),
    lastSeen = LocalDate.parse("2026-10-01"),
)

// The closes the computed series prices the window against. March 2018 is the first point (the three
// papers bought that month, the pre-rename code's own close included); February 2021 rises by exactly
// the FII income it received and June 2021 by exactly the PETR4 dividend, so both returns are
// zero only when the income is netted out.
private suspend fun seedQuotes() {
    getBean<IQuoteRepository>().upsertAll(
        listOf(
            quote(ticker = "BIDI11", date = "2018-03-31", close = "10"),
            quote(ticker = "PETR4", date = "2018-03-31", close = "20"),
            quote(ticker = "VALE3", date = "2018-03-31", close = "30"),
            quote(ticker = "PETR4", date = "2021-01-31", close = "10"),
            quote(ticker = "VALE3", date = "2021-01-31", close = "15"),
            quote(ticker = "INBR32", date = "2021-01-31", close = "10"),
            quote(ticker = "MXRF11", date = "2021-01-31", close = "10"),
            quote(ticker = "PETR4", date = "2021-02-28", close = "10"),
            quote(ticker = "VALE3", date = "2021-02-28", close = "15"),
            quote(ticker = "INBR32", date = "2021-02-28", close = "10"),
            quote(ticker = "MXRF11", date = "2021-02-28", close = "10.1"),
            quote(ticker = "PETR4", date = "2021-05-31", close = "10"),
            quote(ticker = "VALE3", date = "2021-05-31", close = "15"),
            quote(ticker = "INBR32", date = "2021-05-31", close = "10"),
            quote(ticker = "MXRF11", date = "2021-05-31", close = "10"),
            quote(ticker = "PETR4", date = "2021-06-30", close = "10.5"),
            quote(ticker = "VALE3", date = "2021-06-30", close = "15"),
            quote(ticker = "INBR32", date = "2021-06-30", close = "10"),
            quote(ticker = "MXRF11", date = "2021-06-30", close = "10"),
        ),
    )
}

private fun quote(ticker: String, date: String, close: String) =
    Quote(ticker, LocalDate.parse(date), BigDecimal(close), QuoteSource.COTAHIST)

// The three brapi endpoints answer with the committed 2018–2021 scenario fixtures, and the
// benchmark chain gets its own empty answers so the series read never leaves the harness.
private suspend fun stubProviders() = HttpMockService.configureResponses {
    response {
        method = RequestMethod.GET
        endpoint = "/api/v2/tickers/renames"
        body = contract("brapi/scenario-2018-2021/ticker-renames.json")
    }
    response {
        method = RequestMethod.GET
        endpoint = "/api/v2/stocks/dividends"
        body = contract("brapi/scenario-2018-2021/stock-dividends.json")
    }
    response {
        method = RequestMethod.GET
        endpoint = "/api/v2/fii/dividends"
        body = contract("brapi/scenario-2018-2021/fii-dividends.json")
    }
    response {
        method = RequestMethod.GET
        endpoint = "/api/quote/.*"
        payload = mapOf("results" to emptyList<Any>())
    }
    response {
        method = RequestMethod.GET
        endpoint = "/dados/serie/bcdata.sgs.12/dados"
        payload = emptyList<Any>()
    }
    response {
        method = RequestMethod.GET
        endpoint = "/dados/serie/bcdata.sgs.433/dados"
        payload = emptyList<Any>()
    }
}

private fun contract(path: String): ByteArray = File(
    System.getProperty("contractsDir")
        ?: error("contractsDir system property is not set; see backend/build.gradle.kts"),
).resolve(path).readBytes()

private suspend fun uploadRico(): Int {
    val response = getBean<HttpClient>().post("${appBaseUrl()}/imports") {
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("source", "RICO_NOTES")
                    append("destination_object", "TRADES")
                    append(
                        "file",
                        ricoHistoryFixture(),
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"nota-de-corretagem.pdf\"")
                        },
                    )
                },
            ),
        )
    }
    response.status shouldBe HttpStatusCode.Created
    return json(response).get("id").asInt()
}

private suspend fun importB3Split() {
    val response = getBean<HttpClient>().post("${appBaseUrl()}/corporate-actions/import") {
        setBody(
            MultiPartFormDataContent(
                formData {
                    append(
                        "file",
                        b3SplitFixture(),
                        Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"movimentacao.xlsx\"") },
                    )
                },
            ),
        )
    }
    response.status shouldBe HttpStatusCode.Created
    json(response).get("corporate_actions").size() shouldBe 1
}

private suspend fun sync(): JsonNode =
    json(getBean<HttpClient>().post("${appBaseUrl()}/corporate-actions/sync"))

private suspend fun confirmTicker(importId: Int, rowId: Int, ticker: String) {
    val response = getBean<HttpClient>().post("${appBaseUrl()}/imports/$importId/rows/$rowId/ticker") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("ticker" to ticker))
    }
    response.status shouldBe HttpStatusCode.OK
}

private suspend fun review(importId: Int) {
    getBean<HttpClient>().post("${appBaseUrl()}/imports/$importId/review").status shouldBe HttpStatusCode.OK
}

private suspend fun apply(importId: Int): HttpResponse =
    getBean<HttpClient>().post("${appBaseUrl()}/imports/$importId/apply")

private suspend fun rows(importId: Int): JsonNode =
    json(getBean<HttpClient>().get("${appBaseUrl()}/imports/$importId/rows"))

private suspend fun positions(): JsonNode =
    json(getBean<HttpClient>().get("${appBaseUrl()}/positions"))

private suspend fun corporateActions(): JsonNode =
    json(getBean<HttpClient>().get("${appBaseUrl()}/corporate-actions"))

private suspend fun income(): JsonNode =
    json(getBean<HttpClient>().get("${appBaseUrl()}/income"))

private suspend fun tax(period: String): JsonNode =
    json(getBean<HttpClient>().get("${appBaseUrl()}/tax/$period"))

private suspend fun series(): JsonNode =
    json(getBean<HttpClient>().get("${appBaseUrl()}/performance/series"))

private fun assertIncomeNetted(month: JsonNode, valueStart: String, valueEnd: String, income: String) {
    val decomposition = month.get("decomposition")
    month.get("partial").asBoolean() shouldBe false
    decomposition.get("value_start").decimalValue().compareTo(BigDecimal(valueStart)) shouldBe 0
    decomposition.get("value_end").decimalValue().compareTo(BigDecimal(valueEnd)) shouldBe 0
    decomposition.get("net_income").decimalValue().compareTo(BigDecimal(income)) shouldBe 0
    decomposition.get("net_aportes").decimalValue().compareTo(BigDecimal.ZERO) shouldBe 0
    decomposition.get("return_percentage").decimalValue().compareTo(BigDecimal.ZERO) shouldBe 0
}

private fun positionOf(positions: Map<String, JsonNode>, ticker: String, quantity: String, averagePrice: String) {
    val position = positions.getValue(ticker)
    position.get("quantity").decimalValue().compareTo(BigDecimal(quantity)) shouldBe 0
    position.get("average_price").decimalValue().compareTo(BigDecimal(averagePrice)) shouldBe 0
}

private suspend fun json(response: HttpResponse): JsonNode =
    ObjectMapper().readTree(response.bodyAsText())
