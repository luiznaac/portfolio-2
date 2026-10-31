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
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.model.effectiveAssetClass
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant

// A manual classification is a durable owner decision, not a cache of the BDI mapping: it must
// outlive a COTAHIST refresh that re-derives the class. Only a real MySQL round-trip plus the real
// gateway (stubbed at the wire) can prove the override survives the upsert.
@IntegrationTest
class CatalogClassificationTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "a manual classification survives a COTAHIST refresh that re-derives the BDI class" {
        val catalog = getBean<CatalogService>()
        classify("XPTO3", AssetClass.ACOES).status shouldBe HttpStatusCode.OK
        catalog.fetchBy("XPTO3")!!.effectiveAssetClass shouldBe AssetClass.ACOES

        HttpMockService.configureResponses {
            response {
                method = RequestMethod.GET
                endpoint = "/InstDados/SerHist/COTAHIST_D13102026.ZIP"
                body = cotahistZip(cotahistRecord("XPTO3", bdiType = "12"))
            }
        }

        refreshCatalog().status shouldBe HttpStatusCode.OK

        catalog.fetchBy("XPTO3")!!.also {
            it.assetClass shouldBe AssetClass.FIIS
            it.assetClassOverride shouldBe AssetClass.ACOES
            it.effectiveAssetClass shouldBe AssetClass.ACOES
        }
    }

    "revert clears the override and leaves the BDI-derived class in charge" {
        val catalog = getBean<CatalogService>()
        classify("XPTO3", AssetClass.ACOES)

        HttpMockService.configureResponses {
            response {
                method = RequestMethod.GET
                endpoint = "/InstDados/SerHist/COTAHIST_D13102026.ZIP"
                body = cotahistZip(cotahistRecord("XPTO3", bdiType = "12"))
            }
        }
        refreshCatalog()

        revert("XPTO3").status shouldBe HttpStatusCode.OK

        catalog.fetchBy("XPTO3")!!.also {
            it.assetClass shouldBe AssetClass.FIIS
            it.assetClassOverride shouldBe null
            it.effectiveAssetClass shouldBe AssetClass.FIIS
        }
    }

    "the queue lists only held unclassified tickers and the bulk write clears just the chosen ones" {
        getBean<IPositionRepository>().save(position("XPTO3", "10"))
        getBean<IPositionRepository>().save(position("MISS3", "5"))

        val before = ObjectMapper().readTree(
            getBean<HttpClient>().get("http://localhost:8080/tickers/unclassified").bodyAsText(),
        )
        before.size() shouldBe 2
        before.get(0).get("ticker").asText() shouldBe "MISS3"
        before.get(0).get("reason").asText() shouldBe "ABSENT_FROM_LATEST_FILE"
        before.get(1).get("ticker").asText() shouldBe "XPTO3"
        before.get(1).get("short_name").asText() shouldBe "XPTO3"
        before.get(1).get("quantity").decimalValue().compareTo(BigDecimal("10")) shouldBe 0

        val written = ObjectMapper().readTree(
            getBean<HttpClient>().put("http://localhost:8080/tickers/classification") {
                contentType(ContentType.Application.Json)
                setBody("""{"classifications":{"XPTO3":"ACOES"}}""")
            }.bodyAsText(),
        )
        written.size() shouldBe 1
        written.get(0).get("ticker").asText() shouldBe "XPTO3"
        written.get(0).get("asset_class_override").asText() shouldBe "ACOES"

        val after = ObjectMapper().readTree(
            getBean<HttpClient>().get("http://localhost:8080/tickers/unclassified").bodyAsText(),
        )
        after.size() shouldBe 1
        after.get(0).get("ticker").asText() shouldBe "MISS3"
        getBean<CatalogService>().fetchBy("MISS3") shouldBe null
    }

    "a batch that names a class a listed ticker cannot hold writes nothing" {
        getBean<IPositionRepository>().save(position("XPTO3", "10"))

        val response = getBean<HttpClient>().put("http://localhost:8080/tickers/classification") {
            contentType(ContentType.Application.Json)
            setBody("""{"classifications":{"XPTO3":"ACOES","BOND1":"RENDA_FIXA"}}""")
        }

        response.status shouldBe HttpStatusCode.BadRequest

        val queue = ObjectMapper().readTree(
            getBean<HttpClient>().get("http://localhost:8080/tickers/unclassified").bodyAsText(),
        )
        queue.size() shouldBe 1
        queue.get(0).get("ticker").asText() shouldBe "XPTO3"
    }

    "the legacy attribution preview groups an override-only ticker under its manual class" {
        getBean<IPositionRepository>().save(position("XPTO3", "10"))
        getBean<ICatalogRepository>().upsertAll(
            listOf(
                CatalogEntry(
                    ticker = "XPTO3",
                    shortName = "XPTO",
                    bdiType = "??",
                    isin = null,
                    assetKind = AssetKind.UNKNOWN,
                    assetClass = null,
                    assetClassOverride = AssetClass.ACOES,
                    firstSeen = LocalDate.parse("2026-10-01"),
                    lastSeen = LocalDate.parse("2026-10-14"),
                ),
            ),
        )
        getBean<StrategyService>().create(
            StrategyCreation("Legado Ações", AssetClass.ACOES, StrategyKind.MANUAL),
        )

        val preview = ObjectMapper().readTree(
            getBean<HttpClient>().get("http://localhost:8080/positions/legacy-attribution/preview")
                .bodyAsText(),
        )

        preview.get("groups").size() shouldBe 1
        preview.get("groups").get(0).get("asset_class").asText() shouldBe "ACOES"
        preview.get("groups").get(0).get("adds").get(0).get("ticker").asText() shouldBe "XPTO3"
        preview.get("skips").size() shouldBe 0
    }
})

private suspend fun classify(ticker: String, assetClass: AssetClass) =
    getBean<HttpClient>().put("http://localhost:8080/tickers/$ticker/classification") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("asset_class" to assetClass.name))
    }

private suspend fun revert(ticker: String) =
    getBean<HttpClient>().delete("http://localhost:8080/tickers/$ticker/classification")

private suspend fun refreshCatalog() = getBean<HttpClient>().post("http://localhost:8080/tickers/refresh")

private fun position(ticker: String, quantity: String) =
    Position(ticker = ticker, quantity = BigDecimal(quantity), averagePrice = BigDecimal("10"))
