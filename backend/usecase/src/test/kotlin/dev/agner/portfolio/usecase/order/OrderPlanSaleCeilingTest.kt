package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.allocation.model.AllocationPlan
import dev.agner.portfolio.usecase.allocation.model.AssetClass.STOCKS
import dev.agner.portfolio.usecase.allocation.model.ClassNode
import dev.agner.portfolio.usecase.attribution.model.AttributionSummary
import dev.agner.portfolio.usecase.attribution.model.StrategyBalance
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.model.Quote
import dev.agner.portfolio.usecase.listedasset.model.QuoteSource.BRAPI
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.model.StrategyWeight
import dev.agner.portfolio.usecase.trade.model.Trade
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class OrderPlanSaleCeilingTest : StringSpec({
    val env = orderPlanTestEnv()
    val strategyService = env.strategyService
    val strategyWeightRepository = env.strategyWeightRepository
    val strategyEditionService = env.strategyEditionService
    val allocationService = env.allocationService
    val attributionService = env.attributionService
    val listedAssetRepository = env.listedAssetRepository
    val tradeRepository = env.tradeRepository
    val quoteGateway = env.quoteGateway
    val service = env.service

    beforeTest { env.stubDefaults() }

    "should flag the sale-exemption ceiling as exceeded past R$20,000 in stock sales this month" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        // Target matches custody exactly (100 shares) so this ticker generates no order of its
        // own — isolates the ceiling meter to just the already-executed trade below.
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("0.5")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("100"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("100"))),
        )
        coEvery { tradeRepository.fetchByDateRange(any(), any()) } returns listOf(
            Trade.Sell(1, 10, LocalDate.parse("2026-09-05"), BigDecimal("500"), BigDecimal("45.00")),
        )

        val plan = service.computePlan()

        // 500 * 45.00 already sold this month, well past the 20k ceiling on its own
        plan.saleCeiling.monthSold shouldBe BigDecimal("22500.00")
        plan.saleCeiling.remaining shouldBe BigDecimal.ZERO
    }

    "should count a sale of an unregistered asset toward the ceiling" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("0.5")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("100"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("100"))),
        )
        // A sell of an asset with no registered kind: without a kind to look up it cannot be
        // proven to be a FII, so the conservative reading is to count it (understates headroom).
        coEvery { tradeRepository.fetchByDateRange(any(), any()) } returns listOf(
            Trade.Sell(3, 999, LocalDate.parse("2026-09-05"), BigDecimal("100"), BigDecimal("60.00")),
        )

        val plan = service.computePlan()

        plan.saleCeiling.monthSold shouldBe BigDecimal("6000.00")
    }

    "should not count a sale of a registered FII toward the ceiling" {
        val fii = ListedAsset(id = 12, ticker = "KNCR11", kind = AssetKind.FII, name = "KNCR", b3Identifier = "KNCR")
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("0.5")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4, fii)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("100"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("100"))),
        )
        coEvery { attributionService.summarize(12) } returns AttributionSummary(
            custodyQuantity = BigDecimal.ZERO,
            balances = emptyList(),
        )
        coEvery { tradeRepository.fetchByDateRange(any(), any()) } returns listOf(
            Trade.Sell(4, 12, LocalDate.parse("2026-09-05"), BigDecimal("100"), BigDecimal("60.00")),
        )

        val plan = service.computePlan()

        plan.saleCeiling.monthSold shouldBe BigDecimal("0.00")
    }

    "should exclude a day-trade-flagged order from the exemption meter" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("0.5")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("150"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("150"))),
        )
        // A buy already happened today, so the planned sell (150 > ideal 100) is a day trade —
        // taxed at 20% regardless, so its notional must not eat the R$20k exemption.
        coEvery { tradeRepository.fetchByAssetId(10) } returns listOf(
            Trade.Buy(2, 10, LocalDate.parse("2026-09-15"), BigDecimal("10"), BigDecimal("50.00")),
        )

        val plan = service.computePlan()

        plan.orders.single().dayTradeRisk shouldBe true
        plan.saleCeiling.monthSold shouldBe BigDecimal("0.00")
    }

    "should exclude a same-day buy+sell ledger pair from the exemption meter" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("0.5")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("100"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("100"))),
        )
        // A 100-share buy and a 100-share sell on the same day: the sell is the day-trade half of
        // the pair, so it never consumes the exemption.
        coEvery { tradeRepository.fetchByDateRange(any(), any()) } returns listOf(
            Trade.Buy(5, 10, LocalDate.parse("2026-09-10"), BigDecimal("100"), BigDecimal("40.00")),
            Trade.Sell(6, 10, LocalDate.parse("2026-09-10"), BigDecimal("100"), BigDecimal("60.00")),
        )

        val plan = service.computePlan()

        plan.saleCeiling.monthSold shouldBe BigDecimal("0.00")
    }

    "should keep counting a swing sell (no same-day buy) toward the exemption meter" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("0.5")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("100"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("100"))),
        )
        coEvery { tradeRepository.fetchByDateRange(any(), any()) } returns listOf(
            Trade.Buy(7, 10, LocalDate.parse("2026-09-05"), BigDecimal("200"), BigDecimal("40.00")),
            Trade.Sell(8, 10, LocalDate.parse("2026-09-12"), BigDecimal("100"), BigDecimal("60.00")),
        )

        val plan = service.computePlan()

        plan.saleCeiling.monthSold shouldBe BigDecimal("6000.00")
    }
})
