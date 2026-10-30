package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.RequestMethod
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.HttpMockService
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.cotahistRecord
import dev.agner.portfolio.integrationTest.helpers.cotahistZip
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant

// The one combined refresh over the real stack: one daily file feeds the catalog and the latest
// closes, and the response separates a healthy run from a partial one instead of returning zeros.
@IntegrationTest
class TickerRefreshTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "one refresh prices and classifies the held tickers and returns asOf plus empty failures" {
        getBean<IPositionRepository>().save(
            Position(ticker = "PETR4", quantity = BigDecimal("10"), averagePrice = BigDecimal("30")),
        )
        getBean<IPositionRepository>().save(
            Position(ticker = "ABS3", quantity = BigDecimal("5"), averagePrice = BigDecimal("10")),
        )
        HttpMockService.configureResponses {
            response {
                method = RequestMethod.GET
                endpoint = "/InstDados/SerHist/COTAHIST_D13102026.ZIP"
                body = cotahistZip(
                    cotahistRecord("PETR4", bdiType = "02", close = "0000000004892"),
                    cotahistRecord("ABS3", bdiType = "99", close = "0000000010000"),
                )
            }
        }

        val response = getBean<HttpClient>().post("http://localhost:8080/tickers/refresh")
        val payload = ObjectMapper().readTree(response.bodyAsText())

        response.status shouldBe HttpStatusCode.OK
        payload.get("catalog").asInt() shouldBe 2
        payload.get("quotes").asInt() shouldBe 2
        payload.get("as_of").asText() shouldBe "2026-10-13"
        payload.get("failed").size() shouldBe 0
        payload.get("unclassified").size() shouldBe 1
        payload.get("unclassified").get(0).get("ticker").asText() shouldBe "ABS3"
        payload.get("unclassified").get(0).get("reason").asText() shouldBe "UNHANDLED_BDI_CODE"

        getBean<CatalogService>().fetchBy("PETR4")!!.assetClass shouldBe AssetClass.ACOES
        getBean<IQuoteRepository>().fetchAllBy("PETR4").single().close shouldBe BigDecimal("48.920000")
    }

    "loads monthly closes across the ledger's years and a re-run stores the same rows" {
        val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
        getBean<ITradeRepository>().save(
            TradeCreation(
                ticker = "PETR4",
                side = TradeSide.BUY,
                date = LocalDate.parse("2026-01-02"),
                venue = "VISTA",
                quantity = BigDecimal("10"),
                unitPrice = BigDecimal("20"),
                institutionId = institution.id,
            ),
            TradeSource.B3_IMPORT,
            ordinal = 1,
        )
        getBean<IPositionRepository>().save(
            Position(ticker = "PETR4", quantity = BigDecimal("10"), averagePrice = BigDecimal("20")),
        )
        HttpMockService.configureResponses {
            response {
                method = RequestMethod.GET
                endpoint = "/InstDados/SerHist/COTAHIST_D13102026.ZIP"
                body = cotahistZip(cotahistRecord("PETR4", bdiType = "02", close = "0000000021500"))
            }
            response {
                method = RequestMethod.GET
                endpoint = "/InstDados/SerHist/COTAHIST_A2026.ZIP"
                body = cotahistZip(
                    cotahistRecord("PETR4", bdiType = "02", date = "20260131", close = "0000000020000"),
                    cotahistRecord("PETR4", bdiType = "02", date = "20260228", close = "0000000021000"),
                )
            }
        }

        val first = getBean<HttpClient>().post("http://localhost:8080/tickers/refresh")
        val firstBody = ObjectMapper().readTree(first.bodyAsText())
        val firstQuotes = getBean<IQuoteRepository>().fetchAllBy("PETR4").map { it.date to it.close }

        first.status shouldBe HttpStatusCode.OK
        firstBody.get("quotes").asInt() shouldBe 3
        firstBody.get("failed").size() shouldBe 0
        firstQuotes.map { it.first } shouldBe listOf(
            LocalDate.parse("2026-01-31"),
            LocalDate.parse("2026-02-28"),
            LocalDate.parse("2026-10-13"),
        )

        val second = getBean<HttpClient>().post("http://localhost:8080/tickers/refresh")

        second.status shouldBe HttpStatusCode.OK
        getBean<IQuoteRepository>().fetchAllBy("PETR4").map { it.date to it.close } shouldBe firstQuotes
    }

    "a failed daily file still answers 200 with the failure visible in the body" {
        HttpMockService.configureResponses {
            response {
                method = RequestMethod.GET
                endpoint = "/InstDados/SerHist/COTAHIST_D13102026.ZIP"
                httpStatus = 500
            }
        }

        val response = getBean<HttpClient>().post("http://localhost:8080/tickers/refresh")
        val payload = ObjectMapper().readTree(response.bodyAsText())

        response.status shouldBe HttpStatusCode.OK
        payload.get("catalog").asInt() shouldBe 0
        payload.get("quotes").asInt() shouldBe 0
        payload.get("failed").get(0).get("kind").asText() shouldBe "SOURCE"
        payload.get("failed").get(0).get("source").asText() shouldBe "cotahist-daily"
    }
})
