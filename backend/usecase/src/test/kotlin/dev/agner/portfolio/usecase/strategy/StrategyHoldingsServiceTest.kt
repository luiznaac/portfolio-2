package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal

// The manual Strategy detail's read: the bucket's attributed slices, each priced at the current
// quote, with its share of the bucket total.
class StrategyHoldingsServiceTest : StringSpec({
    val strategies = mockk<IStrategyRepository>(relaxed = true)
    val attributions = mockk<IAttributionRepository>(relaxed = true)
    val quotes = mockk<QuoteService>(relaxed = true)
    val service = StrategyHoldingsService(strategies, attributions, quotes)

    beforeTest { clearAllMocks() }

    "returns null when the strategy does not exist" {
        coEvery { strategies.fetchById(404) } returns null

        service.fetch(404) shouldBe null
    }

    "prices each attributed slice and shares it over the bucket total" {
        coEvery { strategies.fetchById(9) } returns manualStrategy
        coEvery { attributions.fetchAll() } returns listOf(
            Attribution("VALE3", 9, BigDecimal("10")),
            Attribution("ITUB4", 9, BigDecimal("20")),
            Attribution("PETR4", 1, BigDecimal("5")),
        )
        coEvery { quotes.latestClose("VALE3") } returns BigDecimal("10")
        coEvery { quotes.latestClose("ITUB4") } returns BigDecimal("5")

        val holdings = service.fetch(9)!!

        holdings.strategyName shouldBe "Legado Ações"
        holdings.totalValue.plain() shouldBe "200"
        holdings.holdings.map { it.ticker } shouldBe listOf("ITUB4", "VALE3")
        holdings.holdings.map { it.value.plain() } shouldBe listOf("100", "100")
        holdings.holdings.map { it.share.plain() } shouldBe listOf("50", "50")
    }

    "values a slice with no stored quote at zero and shares a zero bucket as zero" {
        coEvery { strategies.fetchById(9) } returns manualStrategy
        coEvery { attributions.fetchAll() } returns listOf(Attribution("VALE3", 9, BigDecimal("10")))
        coEvery { quotes.latestClose("VALE3") } returns null

        val holdings = service.fetch(9)!!

        holdings.totalValue.plain() shouldBe "0"
        holdings.holdings.single().value.plain() shouldBe "0"
        holdings.holdings.single().share.plain() shouldBe "0"
    }

    "an empty bucket has no holdings" {
        coEvery { strategies.fetchById(9) } returns manualStrategy
        coEvery { attributions.fetchAll() } returns emptyList()

        val holdings = service.fetch(9)!!

        holdings.totalValue.plain() shouldBe "0"
        holdings.holdings shouldBe emptyList()
    }
})

private val manualStrategy = Strategy(9, "Legado Ações", AssetClass.ACOES, StrategyKind.MANUAL)
