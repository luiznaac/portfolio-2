package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class CatalogControllerTest : DescribeSpec({

    describe("listing the ticker catalog") {

        it("returns 200 with the catalog serialized as snake_case") {
            val catalog = mockk<CatalogService>()
            val quotes = mockk<QuoteService>()
            coEvery { catalog.fetchAll() } returns listOf(petr4)

            testApplication {
                application { installRoutes(CatalogController(catalog, quotes)) }

                val response = client.get("/tickers")
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.OK
                payload.get(0).get("ticker").asText() shouldBe "PETR4"
                payload.get(0).get("short_name").asText() shouldBe "PETROBRAS"
                payload.get(0).get("asset_kind").asText() shouldBe "STOCK"
                payload.get(0).get("asset_class").asText() shouldBe "ACOES"
                payload.get(0).get("last_seen").asText() shouldBe "2026-10-14"
            }
        }
    }

    describe("fetching a single ticker") {

        it("returns 200 with the catalog entry") {
            val catalog = mockk<CatalogService>()
            val quotes = mockk<QuoteService>()
            coEvery { catalog.fetchBy("PETR4") } returns petr4

            testApplication {
                application { installRoutes(CatalogController(catalog, quotes)) }

                val response = client.get("/tickers/PETR4")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get("ticker").asText() shouldBe "PETR4"
            }
        }

        it("returns 404 when the ticker is not in the catalog") {
            val catalog = mockk<CatalogService>()
            val quotes = mockk<QuoteService>()
            coEvery { catalog.fetchBy("XPTO") } returns null

            testApplication {
                application { installRoutes(CatalogController(catalog, quotes)) }

                client.get("/tickers/XPTO").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("listing a ticker's quotes") {

        it("returns 200 with the stored quotes") {
            val catalog = mockk<CatalogService>()
            val quotes = mockk<QuoteService>()
            coEvery { quotes.fetchAllBy("PETR4") } returns listOf(petr4Quote)

            testApplication {
                application { installRoutes(CatalogController(catalog, quotes)) }

                val response = client.get("/tickers/PETR4/quotes")
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.OK
                payload.get(0).get("ticker").asText() shouldBe "PETR4"
                payload.get(0).get("close").asText() shouldBe "48.92"
            }
        }
    }

    describe("refreshing the catalog and EOD quotes") {

        it("returns 200 with the refreshed counts") {
            val catalog = mockk<CatalogService>()
            val quotes = mockk<QuoteService>()
            coEvery { catalog.refresh() } returns 3
            coEvery { quotes.refreshEod() } returns 5

            testApplication {
                application { installRoutes(CatalogController(catalog, quotes)) }

                val response = client.post("/tickers/refresh")
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.OK
                payload.get("catalog").asInt() shouldBe 3
                payload.get("quotes").asInt() shouldBe 5
                coVerify(exactly = 1) { catalog.refresh() }
                coVerify(exactly = 1) { quotes.refreshEod() }
            }
        }
    }
})

private val petr4 = CatalogEntry(
    ticker = "PETR4",
    shortName = "PETROBRAS",
    bdiType = "02",
    isin = "BRPETRACNPR6",
    assetKind = AssetKind.STOCK,
    assetClass = AssetClass.ACOES,
    firstSeen = LocalDate.parse("2026-10-14"),
    lastSeen = LocalDate.parse("2026-10-14"),
)

private val petr4Quote = Quote(
    ticker = "PETR4",
    date = LocalDate.parse("2026-10-14"),
    close = BigDecimal("48.92"),
    source = QuoteSource.COTAHIST,
)
