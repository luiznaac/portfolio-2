package dev.agner.portfolio.integrationTest.tests

import com.github.tomakehurst.wiremock.http.RequestMethod
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.HttpMockService
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.cotahistRecord
import dev.agner.portfolio.integrationTest.helpers.cotahistZip
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.effectiveAssetClass
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.every
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
})

private suspend fun classify(ticker: String, assetClass: AssetClass) =
    getBean<HttpClient>().put("http://localhost:8080/tickers/$ticker/classification") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("asset_class" to assetClass.name))
    }

private suspend fun revert(ticker: String) =
    getBean<HttpClient>().delete("http://localhost:8080/tickers/$ticker/classification")

private suspend fun refreshCatalog() = getBean<HttpClient>().post("http://localhost:8080/tickers/refresh")
