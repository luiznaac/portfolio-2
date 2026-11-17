package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.RequestMethod
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.HttpMockService
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.appBaseUrl
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant

// The one-off brapi sync over the real stack: a stubbed provider feeds renames, a stock event, cash
// income and a FII payment, and the run persists the parsed rows, the income and an audit. A second
// run is a no-op, which is the resumable guarantee the paid one-off month rests on.
@IntegrationTest
class BrapiSyncTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-11-17T10:00:00Z")
        val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
        getBean<ITradeRepository>().save(
            TradeCreation(
                ticker = "PETR4",
                side = TradeSide.BUY,
                date = LocalDate.parse("2018-03-05"),
                venue = "VISTA",
                quantity = BigDecimal("100"),
                unitPrice = BigDecimal("20"),
                institutionId = institution.id,
            ),
            TradeSource.B3_IMPORT,
            ordinal = 1,
        )
        getBean<ITradeRepository>().save(
            TradeCreation(
                ticker = "MXRF11",
                side = TradeSide.BUY,
                date = LocalDate.parse("2020-07-01"),
                venue = "VISTA",
                quantity = BigDecimal("100"),
                unitPrice = BigDecimal("10"),
                institutionId = institution.id,
            ),
            TradeSource.B3_IMPORT,
            ordinal = 1,
        )
        getBean<ICatalogRepository>().upsertAll(
            listOf(
                catalogEntry("PETR4", AssetKind.STOCK, AssetClass.ACOES),
                catalogEntry("MXRF11", AssetKind.FII, AssetClass.FIIS),
            ),
        )
        HttpMockService.configureResponses {
            response {
                method = RequestMethod.GET
                endpoint = "/api/v2/tickers/renames"
                payload = mapOf("results" to emptyList<Any>(), "count" to 0)
            }
            response {
                method = RequestMethod.GET
                endpoint = "/api/v2/stocks/dividends"
                payload = stockDividends
            }
            response {
                method = RequestMethod.GET
                endpoint = "/api/v2/fii/dividends"
                payload = fundDividends
            }
        }
    }

    "persists the parsed events, the income and the audit, and a re-run changes nothing" {
        val first = getBean<HttpClient>().post("${appBaseUrl()}/corporate-actions/sync")
        val firstBody = ObjectMapper().readTree(first.bodyAsText())

        first.status shouldBe HttpStatusCode.OK
        firstBody.get("ticker_count").asInt() shouldBe 2
        firstBody.get("fetched").asInt() shouldBe 4
        firstBody.get("applied").asInt() shouldBe 1
        firstBody.get("parked").asInt() shouldBe 1
        firstBody.get("income").asInt() shouldBe 2
        firstBody.get("failed_tickers").size() shouldBe 0
        firstBody.get("window_from").asText() shouldBe "2018-03-05"
        firstBody.get("as_of").asText() shouldBe "2026-11-17"

        val actions = ObjectMapper().readTree(
            getBean<HttpClient>()
                .get("${appBaseUrl()}/corporate-actions")
                .bodyAsText(),
        )
        actions.size() shouldBe 2
        actions.get(0).get("source").asText() shouldBe "BRAPI"
        actions.map { it.get("status").asText() }.toSet() shouldBe setOf("APPLIED", "NEEDS_REVIEW")

        val income = ObjectMapper().readTree(getBean<HttpClient>().get("${appBaseUrl()}/income").bodyAsText())
        income.size() shouldBe 2
        income.map { it.get("provenance").asText() }.toSet() shouldBe setOf("BRAPI")
        income.first { it.get("ticker").asText() == "PETR4" }.get("estimated").asBoolean() shouldBe true
        income.first { it.get("ticker").asText() == "MXRF11" }.get("estimated").asBoolean() shouldBe false

        val second = getBean<HttpClient>().post("${appBaseUrl()}/corporate-actions/sync")
        val secondBody = ObjectMapper().readTree(second.bodyAsText())

        second.status shouldBe HttpStatusCode.OK
        secondBody.get("applied").asInt() shouldBe 0
        secondBody.get("income").asInt() shouldBe 0
        ObjectMapper()
            .readTree(getBean<HttpClient>().get("${appBaseUrl()}/corporate-actions").bodyAsText())
            .size() shouldBe 2
        ObjectMapper()
            .readTree(getBean<HttpClient>().get("${appBaseUrl()}/income").bodyAsText())
            .size() shouldBe 2
    }
})

private fun catalogEntry(ticker: String, kind: AssetKind, assetClass: AssetClass) = CatalogEntry(
    ticker = ticker,
    shortName = ticker,
    bdiType = if (kind == AssetKind.FII) "12" else "02",
    isin = null,
    assetKind = kind,
    assetClass = assetClass,
    firstSeen = LocalDate.parse("2018-01-02"),
    lastSeen = LocalDate.parse("2026-10-01"),
)

private val stockDividends = mapOf(
    "results" to listOf(
        mapOf(
            "requestedSymbol" to "PETR4",
            "symbol" to "PETR4",
            "changed" to false,
            "data" to mapOf(
                "cashDividends" to listOf(
                    mapOf(
                        "label" to "DIVIDENDO",
                        "rate" to 0.5,
                        "lastDatePrior" to "2019-05-01T03:00:00.000Z",
                        "exDate" to "2019-05-02T03:00:00.000Z",
                        "paymentDate" to "2019-06-10T03:00:00.000Z",
                    ),
                ),
                "stockDividends" to listOf(
                    mapOf(
                        "label" to "DESDOBRAMENTO",
                        "factor" to 2,
                        "exDate" to "2020-01-10T03:00:00.000Z",
                    ),
                ),
                "subscriptions" to emptyList<Any>(),
            ),
        ),
    ),
)

private val fundDividends = mapOf(
    "dividends" to listOf(
        mapOf(
            "symbol" to "MXRF11",
            "label" to "RENDIMENTO",
            "rate" to 0.1,
            "rawRate" to 0.1,
            "lastDatePrior" to "2021-01-29T03:00:00.000Z",
            "paymentDate" to "2021-02-15T03:00:00.000Z",
        ),
        mapOf(
            "symbol" to "MXRF11",
            "label" to "AMORTIZAÇÃO",
            "rate" to 0.2,
            "rawRate" to 0.2,
            "exDate" to null,
            "paymentDate" to "2021-03-15T03:00:00.000Z",
        ),
    ),
)
