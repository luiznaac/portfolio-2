package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.allocation.AllocationService
import dev.agner.portfolio.usecase.allocation.model.AllocationPlan
import dev.agner.portfolio.usecase.allocation.model.AssetClass.ACOES
import dev.agner.portfolio.usecase.allocation.model.ClassNode
import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.attribution.model.AttributionSummary
import dev.agner.portfolio.usecase.attribution.model.StrategyBalance
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind.STOCK
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.model.Quote
import dev.agner.portfolio.usecase.listedasset.model.QuoteSource.BRAPI
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.model.OrderKind.BUY
import dev.agner.portfolio.usecase.order.model.OrderKind.FULL_EXIT
import dev.agner.portfolio.usecase.order.model.OrderKind.NEW_ENTRY
import dev.agner.portfolio.usecase.order.model.OrderKind.SELL
import dev.agner.portfolio.usecase.strategy.StrategyEditionService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyEdition
import dev.agner.portfolio.usecase.strategy.model.StrategyEditionWithDiff
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.model.StrategyWeight
import dev.agner.portfolio.usecase.strategy.repository.IStrategyWeightRepository
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OrderPlanServiceTest : StringSpec({
    val strategyService = mockk<StrategyService>()
    val strategyWeightRepository = mockk<IStrategyWeightRepository>()
    val strategyEditionService = mockk<StrategyEditionService>()
    val allocationService = mockk<AllocationService>()
    val attributionService = mockk<AttributionService>()
    val listedAssetRepository = mockk<IListedAssetRepository>()
    val tradeRepository = mockk<ITradeRepository>()
    val quoteGateway = mockk<IQuoteGateway>()
    val clock = mockk<Clock>()

    val service = OrderPlanService(
        strategyService,
        strategyWeightRepository,
        strategyEditionService,
        allocationService,
        attributionService,
        listedAssetRepository,
        tradeRepository,
        quoteGateway,
        TransferMatcher(),
        clock,
    )

    val top = Strategy(id = 1, name = "Top", assetClass = ACOES)
    val petr4 = ListedAsset(id = 10, ticker = "PETR4", kind = STOCK, name = "Petrobras", b3Identifier = "PETROBRAS")
    val vale3 = ListedAsset(id = 11, ticker = "VALE3", kind = STOCK, name = "Vale", b3Identifier = "VALE")
    val today = LocalDate.parse("2026-09-15")

    beforeTest {
        clearAllMocks()
        every { clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        every { clock.zone } returns ZoneOffset.UTC
        coEvery { tradeRepository.fetchByAssetId(any()) } returns emptyList()
        coEvery { tradeRepository.fetchAll() } returns emptyList()
    }

    fun edition(strategyId: Int, targets: List<StrategyTarget>) = StrategyEditionWithDiff(
        edition = StrategyEdition(1, strategyId, LocalDate.parse("2026-09-01"), null, targets),
        diff = null,
    )

    "should propose a sell for a strategy holding more than its target and a buy for less" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
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

        plan.transferSuggestions shouldBe emptyList()
        plan.saleCeiling.monthSold shouldBe BigDecimal("2500.00")
    }

    "should flag the sale-exemption ceiling as exceeded past R$20,000 in stock sales this month" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
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
        coEvery { tradeRepository.fetchAll() } returns listOf(
            Trade(1, 10, LocalDate.parse("2026-09-05"), BigDecimal("-500"), BigDecimal("45.00")),
        )

        val plan = service.computePlan()

        // 500 * 45.00 already sold this month, well past the 20k ceiling on its own
        plan.saleCeiling.monthSold shouldBe BigDecimal("22500.00")
        plan.saleCeiling.remaining shouldBe BigDecimal.ZERO
    }

    "should flag a day-trade risk when a trade already exists today opposite the planned order" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
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
            Trade(2, 10, LocalDate.parse("2026-09-15"), BigDecimal("10"), BigDecimal("50.00")),
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
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
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
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
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
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
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
        plan.transferSuggestions shouldBe emptyList()
        plan.saleCeiling.monthSold shouldBe BigDecimal.ZERO
    }

    "should propose no order when custody already matches the sum of the ideals" {
        val dividendos = Strategy(id = 2, name = "Dividendos", assetClass = ACOES)
        coEvery { strategyService.fetchAll() } returns listOf(top, dividendos)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.5"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.5"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
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
        plan.transferSuggestions shouldBe emptyList()
    }

    "should propose a free transfer when the attribution is off but custody is already right" {
        val dividendos = Strategy(id = 2, name = "Dividendos", assetClass = ACOES)
        val smallCaps = Strategy(id = 3, name = "Small Caps", assetClass = ACOES)
        coEvery { strategyService.fetchAll() } returns listOf(top, dividendos, smallCaps)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.5"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.25"), LocalDate.parse("2026-01-01")),
            StrategyWeight(3, 3, BigDecimal("0.25"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(any()) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        // Ideals are 100 / 50 / 50 = 200 shares, exactly custody — so nothing has to trade. The
        // attribution is lopsided anyway, and re-leveling it is a free transfer: exactly the case
        // the whole transfer-before-trading rule exists for.
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("200"),
            balances = listOf(
                StrategyBalance(1, "Top", BigDecimal("200")),
                StrategyBalance(2, "Dividendos", BigDecimal.ZERO),
                StrategyBalance(3, "Small Caps", BigDecimal.ZERO),
            ),
        )

        val plan = service.computePlan()

        plan.orders shouldBe emptyList()
        plan.transferSuggestions.associate { it.toStrategyId to it.quantity } shouldBe mapOf(
            2 to BigDecimal("50"),
            3 to BigDecimal("50"),
        )
        plan.transferSuggestions.all { it.fromStrategyId == 1 } shouldBe true
    }

    "should emit a transfer suggestion and a residual net order together for the same ticker" {
        val dividendos = Strategy(id = 2, name = "Dividendos", assetClass = ACOES)
        coEvery { strategyService.fetchAll() } returns listOf(top, dividendos)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.5"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.5"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
        )
        // Each strategy is owed R$5,000 at R$50.00 = 100 shares, so 200 in total — but the books
        // only say 180. Top is 30 over its ideal, Dividendos 50 short: a free transfer covers the
        // first 30, and the remaining 20 shares are still missing from the portfolio as a whole.
        coEvery { strategyEditionService.fetchEditions(any()) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("180"),
            balances = listOf(
                StrategyBalance(1, "Top", BigDecimal("130")),
                StrategyBalance(2, "Dividendos", BigDecimal("50")),
            ),
        )

        val plan = service.computePlan()

        plan.transferSuggestions.single().let {
            it.listedAssetId shouldBe 10
            it.ticker shouldBe "PETR4"
            it.fromStrategyId shouldBe 1
            it.toStrategyId shouldBe 2
            it.quantity shouldBe BigDecimal("30")
        }
        plan.orders.single().let {
            it.ticker shouldBe "PETR4"
            it.kind shouldBe BUY
            it.quantity shouldBe BigDecimal("20")
        }
    }

    "should not plan for an unregistered ticker and should not crash on it" {
        coEvery { strategyService.fetchAll() } returns listOf(top)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns
            listOf(StrategyWeight(1, 1, BigDecimal("1.0000"), LocalDate.parse("2026-01-01")))
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("10000.00"),
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
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
