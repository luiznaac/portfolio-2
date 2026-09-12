package dev.agner.portfolio.gateway.listedasset

import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.model.Quote
import dev.agner.portfolio.usecase.listedasset.model.QuoteSource.BRAPI
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class BrapiGatewayTest : StringSpec({
    "should serve a repeated ticker from the cache instead of calling brapi twice" {
        val quote = Quote(BigDecimal("38.50"), LocalDate(2026, 9, 8), BRAPI)
        var fetches = 0
        val gateway = object : BrapiGateway(mockk<HttpClient>(relaxed = true), "https://brapi.test", "token") {
            override suspend fun fetchQuote(asset: ListedAsset): Quote? {
                fetches++
                return quote
            }
        }
        val asset = ListedAsset(1, "PETR4", AssetKind.STOCK, "Petrobras", "PETROBRAS")

        gateway.getQuote(asset) shouldBe quote
        gateway.getQuote(asset) shouldBe quote

        fetches shouldBe 1
    }
})
