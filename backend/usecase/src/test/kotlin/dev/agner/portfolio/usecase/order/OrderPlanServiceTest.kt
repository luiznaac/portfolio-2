package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.allocation.model.AllocationPlan
import dev.agner.portfolio.usecase.allocation.model.AssetClass.STOCKS
import dev.agner.portfolio.usecase.allocation.model.ClassNode
import dev.agner.portfolio.usecase.attribution.model.AttributionSummary
import dev.agner.portfolio.usecase.attribution.model.StrategyBalance
import dev.agner.portfolio.usecase.listedasset.model.Quote
import dev.agner.portfolio.usecase.listedasset.model.QuoteSource.BRAPI
import dev.agner.portfolio.usecase.order.model.OrderKind.BUY
import dev.agner.portfolio.usecase.order.model.OrderKind.FULL_EXIT
import dev.agner.portfolio.usecase.order.model.OrderKind.NEW_ENTRY
import dev.agner.portfolio.usecase.order.model.OrderKind.SELL
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyEdition
import dev.agner.portfolio.usecase.strategy.model.StrategyEditionWithDiff
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.model.StrategyWeight
import dev.agner.portfolio.usecase.trade.model.Trade
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class OrderPlanServiceTest : StringSpec({
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

    "should propose a sell for a strategy holding more than its target and a buy for less" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("0.5")), StrategyTarget("VALE3", BigDecimal("0.5")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4, vale3)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        coEvery { quoteGateway.getQuote(vale3) } returns Quote(BigDecimal("25.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("150"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("150"))),
        )
        coEvery { attributionService.summarize(11) } returns AttributionSummary(
            custodyQuantity = BigDecimal("100"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("100"))),
        )

        val plan = service.computePlan()

        val petrOrder = plan.orders.single { it.ticker == "PETR4" }
        petrOrder.kind shouldBe SELL
        petrOrder.quantity shouldBe BigDecimal("50")
        petrOrder.notional shouldBe BigDecimal("2500.00")

        val valeOrder = plan.orders.single { it.ticker == "VALE3" }
        valeOrder.kind shouldBe BUY
        valeOrder.quantity shouldBe BigDecimal("100")

        plan.transferProposals shouldBe emptyList()
        plan.saleCeiling.monthSold shouldBe BigDecimal("2500.00")
    }

    "should flag a day-trade risk when a trade already exists today opposite the planned order" {
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
        // A buy already happened today — the planned order here will be a sell (150 > ideal 100)
        coEvery { tradeRepository.fetchByAssetId(10) } returns listOf(
            Trade.Buy(2, 10, LocalDate.parse("2026-09-15"), BigDecimal("10"), BigDecimal("50.00")),
        )

        val plan = service.computePlan()

        plan.orders.single().dayTradeRisk shouldBe true
    }

    "should zero out the ideal of a ticker that no longer appears in the latest edition" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        // Two editions: an older one that still wanted PETR4, and the current one that dropped it.
        // Only the latest may drive the plan, so the ticker is a full exit rather than a top-up.
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
            StrategyEditionWithDiff(
                edition = StrategyEdition(
                    2,
                    1,
                    LocalDate.parse("2026-09-10"),
                    null,
                    listOf(StrategyTarget("VALE3", BigDecimal("1.0"))),
                ),
                diff = null,
            ),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4, vale3)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        coEvery { quoteGateway.getQuote(vale3) } returns Quote(BigDecimal("25.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("150"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("150"))),
        )
        coEvery { attributionService.summarize(11) } returns AttributionSummary(
            custodyQuantity = BigDecimal.ZERO,
            balances = emptyList(),
        )

        val plan = service.computePlan()

        val exit = plan.orders.single { it.ticker == "PETR4" }
        exit.kind shouldBe FULL_EXIT
        exit.quantity shouldBe BigDecimal("150")
        // 150 * 50.00 of stock sold — straight past the exemption ceiling, as a FULL_EXIT still counts.
        plan.saleCeiling.monthSold shouldBe BigDecimal("7500.00")

        val entry = plan.orders.single { it.ticker == "VALE3" }
        entry.kind shouldBe NEW_ENTRY
        // 100 shares of the R$10,000 ideal at R$25.00
        entry.quantity shouldBe BigDecimal("400")
    }

    "should floor a partial share instead of rounding up past the strategy's capital" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("0.3333")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("30.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal.ZERO,
            balances = emptyList(),
        )

        val plan = service.computePlan()

        // 10000 * 0.3333 / 30 = 111.1 — floored to 111, so the order never overspends the ideal.
        plan.orders.single().quantity shouldBe BigDecimal("111")
    }

    "should plan no order for a ticker it cannot quote" {
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
        coEvery { quoteGateway.getQuote(petr4) } returns null
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("150"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("150"))),
        )

        val plan = service.computePlan()

        // Without a price the ideal is unknowable, so the plan declines to trade rather than
        // guessing — and proposes no transfer to a strategy it cannot size either.
        plan.orders shouldBe emptyList()
        plan.transferProposals shouldBe emptyList()
        plan.saleCeiling.monthSold shouldBe BigDecimal("0.00")
    }

    "should propose no order when custody already matches the sum of the ideals" {
        val dividendos = Strategy(id = 2, name = "Dividendos", assetClass = STOCKS)
        coEvery { strategyService.fetchAll() } returns listOf(top, dividendos)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.5"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.5"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(any()) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        // Each strategy is owed R$5,000 at R$50.00, so 100 shares each and 200 in total — and the
        // books already say exactly that, one balance per strategy. Nothing to fix, nothing to
        // trade, and no delta to transfer.
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("200"),
            balances = listOf(
                StrategyBalance(1, "Top", BigDecimal("100")),
                StrategyBalance(2, "Dividendos", BigDecimal("100")),
            ),
        )

        val plan = service.computePlan()

        plan.orders shouldBe emptyList()
        plan.transferProposals shouldBe emptyList()
    }

    "should not plan for an unregistered ticker and should not crash on it" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        // The edition wants a ticker the app has never registered, next to one it has. Both get
        // weight 1.0, so the registered one is simply undervalued relative to its own ideal.
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("1.0")), StrategyTarget("XPTO3", BigDecimal("1.0")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        // R$10,000 of capital at R$50.00 is 200 shares, against 100 held: a 100-share top-up.
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("100"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("100"))),
        )

        val plan = service.computePlan()

        plan.orders.single().let {
            it.ticker shouldBe "PETR4"
            it.kind shouldBe BUY
            it.quantity shouldBe BigDecimal("100")
        }
    }
})
