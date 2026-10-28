package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.InvalidCatalogClassException
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

    describe("classifying a ticker") {

        it("sets the manual class override and returns the updated entry") {
            val catalog = mockk<CatalogService>()
            val quotes = mockk<QuoteService>()
            coEvery { catalog.classify("XPTO3", AssetClass.ACOES) } returns xpto3.copy(
                assetClassOverride = AssetClass.ACOES,
            )

            testApplication {
                application { installRoutes(CatalogController(catalog, quotes)) }

                val response = client.put("/tickers/XPTO3/classification") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"asset_class":"ACOES"}""")
                }
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.OK
                payload.get("asset_class_override").asText() shouldBe "ACOES"
                payload.has("asset_class") shouldBe false
            }
        }

        it("rejects a class a listed ticker cannot hold with the canonical 400") {
            val catalog = mockk<CatalogService>()
            val quotes = mockk<QuoteService>()
            coEvery { catalog.classify("XPTO3", AssetClass.RENDA_FIXA) } throws
                InvalidCatalogClassException(AssetClass.RENDA_FIXA)

            testApplication {
                application { installRoutes(CatalogController(catalog, quotes)) }

                val response = client.put("/tickers/XPTO3/classification") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"asset_class":"RENDA_FIXA"}""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                    "invalid-catalog-class"
            }
        }

        it("clears the override on revert") {
            val catalog = mockk<CatalogService>()
            val quotes = mockk<QuoteService>()
            coEvery { catalog.revertClassification("XPTO3") } returns xpto3.copy(
                assetClass = AssetClass.FIIS,
            )

            testApplication {
                application { installRoutes(CatalogController(catalog, quotes)) }

                val response = client.delete("/tickers/XPTO3/classification")
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.OK
                payload.has("asset_class_override") shouldBe false
                payload.get("asset_class").asText() shouldBe "FIIS"
                coVerify(exactly = 1) { catalog.revertClassification("XPTO3") }
            }
        }

        it("returns 404 when reverting a ticker the catalog does not know") {
            val catalog = mockk<CatalogService>()
            val quotes = mockk<QuoteService>()
            coEvery { catalog.revertClassification("MISS3") } returns null

            testApplication {
                application { installRoutes(CatalogController(catalog, quotes)) }

                client.delete("/tickers/MISS3/classification").status shouldBe HttpStatusCode.NotFound
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

private val xpto3 = CatalogEntry(
    ticker = "XPTO3",
    shortName = "XPTO",
    bdiType = "??",
    isin = null,
    assetKind = AssetKind.UNKNOWN,
    assetClass = null,
    firstSeen = LocalDate.parse("2026-10-14"),
    lastSeen = LocalDate.parse("2026-10-14"),
)

private val petr4Quote = Quote(
    ticker = "PETR4",
    date = LocalDate.parse("2026-10-14"),
    close = BigDecimal("48.92"),
    source = QuoteSource.COTAHIST,
)
