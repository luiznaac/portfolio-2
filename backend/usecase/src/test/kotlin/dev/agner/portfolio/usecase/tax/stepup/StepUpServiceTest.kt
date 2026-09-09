package dev.agner.portfolio.usecase.tax.stepup

import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.model.Quote
import dev.agner.portfolio.usecase.listedasset.model.QuoteSource.BRAPI
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.SaleCeiling
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StepUpServiceTest : StringSpec({
    val listedAssetRepository = mockk<IListedAssetRepository>()
    val tradeRepository = mockk<ITradeRepository>()
    val corporateActionRepository = mockk<ICorporateActionRepository>()
    val quoteGateway = mockk<IQuoteGateway>()
    val orderPlanService = mockk<OrderPlanService>()
    val clock = mockk<Clock>()

    val service = StepUpService(
        listedAssetRepository,
        tradeRepository,
        corporateActionRepository,
        quoteGateway,
        AveragePriceCalculator(),
        orderPlanService,
        StepUpPlanner(),
        clock,
    )

    val stock = ListedAsset(1, "PETR4", AssetKind.STOCK, "Petrobras", "PETROBRAS")
    val fii = ListedAsset(2, "MXRF11", AssetKind.FII, "Maxi Renda", "MXRF")

    beforeTest {
        every { clock.instant() } returns Instant.parse("2026-09-08T12:00:00Z")
        every { clock.zone } returns ZoneOffset.UTC
        coEvery { orderPlanService.computePlan() } returns OrderPlan(
            orders = emptyList(),
            transferSuggestions = emptyList(),
            saleCeiling = SaleCeiling(BigDecimal.ZERO, BigDecimal("20000.00"), BigDecimal("20000.00")),
        )
        coEvery { corporateActionRepository.fetchByAssetId(any()) } returns emptyList()
    }

    "should exclude FIIs from step-up candidates" {
        coEvery { listedAssetRepository.fetchAll() } returns listOf(stock, fii)
        coEvery { tradeRepository.fetchByAssetId(1) } returns listOf(
            Trade(1, 1, LocalDate(2026, 8, 1), BigDecimal("100"), BigDecimal("10.00")),
        )
        coEvery { tradeRepository.fetchByAssetId(2) } returns listOf(
            Trade(2, 2, LocalDate(2026, 8, 1), BigDecimal("100"), BigDecimal("10.00")),
        )
        coEvery { quoteGateway.getQuote(stock) } returns Quote(BigDecimal("15.00"), LocalDate(2026, 9, 8), BRAPI)

        val plan = service.plan()

        plan.suggestions.map { it.ticker } shouldBe listOf("PETR4")
    }

    "should exclude a ticker already bought today, to avoid a day trade" {
        coEvery { listedAssetRepository.fetchAll() } returns listOf(stock)
        coEvery { tradeRepository.fetchByAssetId(1) } returns listOf(
            Trade(1, 1, LocalDate(2026, 8, 1), BigDecimal("100"), BigDecimal("10.00")),
            Trade(2, 1, LocalDate(2026, 9, 8), BigDecimal("10"), BigDecimal("14.00")),
        )

        val plan = service.plan()

        plan.suggestions shouldBe emptyList()
    }

    "should use the order plan's remaining ceiling as the step-up budget" {
        coEvery { orderPlanService.computePlan() } returns OrderPlan(
            orders = emptyList(),
            transferSuggestions = emptyList(),
            saleCeiling = SaleCeiling(BigDecimal("19500.00"), BigDecimal("20000.00"), BigDecimal("500.00")),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(stock)
        coEvery { tradeRepository.fetchByAssetId(1) } returns listOf(
            Trade(1, 1, LocalDate(2026, 8, 1), BigDecimal("100"), BigDecimal("10.00")),
        )
        coEvery { quoteGateway.getQuote(stock) } returns Quote(BigDecimal("20.00"), LocalDate(2026, 9, 8), BRAPI)

        val plan = service.plan()

        plan.suggestions[0].quantity shouldBe BigDecimal("25")
    }
})
