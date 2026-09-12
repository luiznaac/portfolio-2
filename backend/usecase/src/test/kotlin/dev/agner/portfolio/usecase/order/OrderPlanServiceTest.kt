package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.allocation.AllocationService
import dev.agner.portfolio.usecase.allocation.model.AllocationPlan
import dev.agner.portfolio.usecase.allocation.model.AssetClass.ACOES
import dev.agner.portfolio.usecase.allocation.model.ClassNode
import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.attribution.model.AttributionSummary
import dev.agner.portfolio.usecase.attribution.model.StrategyBalance
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.AssetKind.STOCK
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.model.Quote
import dev.agner.portfolio.usecase.listedasset.model.QuoteSource.BRAPI
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.model.OrderKind.BUY
import dev.agner.portfolio.usecase.order.model.OrderKind.FULL_EXIT
import dev.agner.portfolio.usecase.order.model.OrderKind.NEW_ENTRY
import dev.agner.portfolio.usecase.order.model.OrderKind.SELL
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.APLICADA
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.PENDENTE
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.REJEITADA
import dev.agner.portfolio.usecase.order.model.TransferSettings
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import dev.agner.portfolio.usecase.order.repository.ITransferSettingsRepository
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
    val transferProposalRepository = mockk<ITransferProposalRepository>()
    val transferSettingsRepository = mockk<ITransferSettingsRepository>()
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
        transferProposalRepository,
        transferSettingsRepository,
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
        coEvery { tradeRepository.fetchByDateRange(any(), any()) } returns emptyList()
        coEvery { transferSettingsRepository.fetch() } returns TransferSettings(BigDecimal.ZERO)
    }

    fun edition(strategyId: Int, targets: List<StrategyTarget>) = StrategyEditionWithDiff(
        edition = StrategyEdition(1, strategyId, LocalDate.parse("2026-09-01"), null, targets),
        diff = null,
    )

    fun proposal(
        toStrategyId: Int,
        toStrategyName: String,
        quantity: String,
        status: TransferProposalStatus = PENDENTE,
    ) = TransferProposal(
        id = toStrategyId,
        month = LocalDate.parse("2026-09-01"),
        listedAssetId = 10,
        ticker = "PETR4",
        fromStrategyId = 1,
        fromStrategyName = "Top",
        toStrategyId = toStrategyId,
        toStrategyName = toStrategyName,
        proposedQuantity = BigDecimal(quantity),
        appliedQuantity = null,
        status = status,
        decidedAt = null,
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

        plan.transferProposals shouldBe emptyList()
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
        coEvery { tradeRepository.fetchByDateRange(any(), any()) } returns listOf(
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

    "should create a new pending transfer proposal for a fresh excess/shortage pairing" {
        val div = Strategy(id = 2, name = "Dividendos", assetClass = ACOES)
        coEvery { strategyService.fetchAll() } returns listOf(top, div)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("20000.00"),
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("20000.00"), BigDecimal("20000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
        )
        coEvery { strategyEditionService.fetchEditions(2) } returns listOf(
            edition(2, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        // Top has 50 more than its 100-share ideal; Dividendos has 50 fewer than its own —
        // a clean transfer pairing, net custody already matches total ideal (no real order).
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("200"),
            balances = listOf(
                StrategyBalance(1, "Top", BigDecimal("150")),
                StrategyBalance(2, "Dividendos", BigDecimal("50")),
            ),
        )
        coEvery { transferProposalRepository.find(any(), 10, 1, 2) } returns null
        val saved = TransferProposal(
            id = 7,
            month = LocalDate.parse("2026-09-01"),
            listedAssetId = 10,
            ticker = "PETR4",
            fromStrategyId = 1,
            fromStrategyName = "Top",
            toStrategyId = 2,
            toStrategyName = "Dividendos",
            proposedQuantity = BigDecimal("50"),
            appliedQuantity = null,
            status = PENDENTE,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.save(any()) } returns saved

        val plan = service.computePlan()

        plan.transferProposals shouldBe listOf(saved)
    }

    "should keep a pending proposal whose quantity is numerically equal but scaled differently" {
        val div = Strategy(id = 2, name = "Dividendos", assetClass = ACOES)
        coEvery { strategyService.fetchAll() } returns listOf(top, div)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("20000.00"),
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("20000.00"), BigDecimal("20000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
        )
        coEvery { strategyEditionService.fetchEditions(2) } returns listOf(
            edition(2, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("200"),
            balances = listOf(
                StrategyBalance(1, "Top", BigDecimal("150")),
                StrategyBalance(2, "Dividendos", BigDecimal("50")),
            ),
        )
        // The DB reads back scale 8; the computed match arrives at scale 0. Same value, so the
        // proposal must be returned as-is instead of being "refreshed" on every read.
        val existing = TransferProposal(
            id = 8,
            month = LocalDate.parse("2026-09-01"),
            listedAssetId = 10,
            ticker = "PETR4",
            fromStrategyId = 1,
            fromStrategyName = "Top",
            toStrategyId = 2,
            toStrategyName = "Dividendos",
            proposedQuantity = BigDecimal("50.00000000"),
            appliedQuantity = null,
            status = PENDENTE,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.find(any(), 10, 1, 2) } returns existing

        val plan = service.computePlan()

        plan.transferProposals shouldBe listOf(existing)
        io.mockk.coVerify(exactly = 0) { transferProposalRepository.updateProposedQuantity(any(), any()) }
    }

    "should not resurrect a transfer proposal rejected earlier this month" {
        val div = Strategy(id = 2, name = "Dividendos", assetClass = ACOES)
        coEvery { strategyService.fetchAll() } returns listOf(top, div)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("20000.00"),
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("20000.00"), BigDecimal("20000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
        )
        coEvery { strategyEditionService.fetchEditions(2) } returns listOf(
            edition(2, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("200"),
            balances = listOf(
                StrategyBalance(1, "Top", BigDecimal("150")),
                StrategyBalance(2, "Dividendos", BigDecimal("50")),
            ),
        )
        coEvery { transferProposalRepository.find(any(), 10, 1, 2) } returns TransferProposal(
            id = 3,
            month = LocalDate.parse("2026-09-01"),
            listedAssetId = 10,
            ticker = "PETR4",
            fromStrategyId = 1,
            fromStrategyName = "Top",
            toStrategyId = 2,
            toStrategyName = "Dividendos",
            proposedQuantity = BigDecimal("50"),
            appliedQuantity = null,
            status = REJEITADA,
            decidedAt = kotlinx.datetime.LocalDateTime.parse("2026-09-10T10:00:00"),
        )

        val plan = service.computePlan()

        plan.transferProposals shouldBe emptyList()
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
        plan.transferProposals shouldBe emptyList()
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
        plan.transferProposals shouldBe emptyList()
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
        coEvery { transferProposalRepository.find(any(), any(), any(), any()) } returns null
        coEvery { transferProposalRepository.save(match { it.toStrategyId == 2 }) } returns
            proposal(2, "Dividendos", "50")
        coEvery { transferProposalRepository.save(match { it.toStrategyId == 3 }) } returns
            proposal(3, "Small Caps", "50")

        val plan = service.computePlan()

        plan.orders shouldBe emptyList()
        plan.transferProposals.associate { it.toStrategyId to it.proposedQuantity } shouldBe mapOf(
            2 to BigDecimal("50"),
            3 to BigDecimal("50"),
        )
        plan.transferProposals.all { it.fromStrategyId == 1 } shouldBe true
    }

    "should emit a transfer proposal and a residual net order together for the same ticker" {
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
        coEvery { transferProposalRepository.find(any(), any(), any(), any()) } returns null
        coEvery { transferProposalRepository.save(any()) } returns proposal(2, "Dividendos", "30")

        val plan = service.computePlan()

        plan.transferProposals.single().let {
            it.listedAssetId shouldBe 10
            it.ticker shouldBe "PETR4"
            it.fromStrategyId shouldBe 1
            it.toStrategyId shouldBe 2
            it.proposedQuantity shouldBe BigDecimal("30")
        }
        plan.orders.single().let {
            it.ticker shouldBe "PETR4"
            it.kind shouldBe BUY
            it.quantity shouldBe BigDecimal("20")
        }
    }

    "should count a sale of an unregistered asset toward the ceiling" {
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
            custodyQuantity = BigDecimal("100"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("100"))),
        )
        // A sell of an asset with no registered kind: without a kind to look up it cannot be
        // proven to be a FII, so the conservative reading is to count it (understates headroom).
        coEvery { tradeRepository.fetchByDateRange(any(), any()) } returns listOf(
            Trade(3, 999, LocalDate.parse("2026-09-05"), BigDecimal("-100"), BigDecimal("60.00")),
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
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("10000.00"), BigDecimal("10000.00"))),
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
            Trade(4, 12, LocalDate.parse("2026-09-05"), BigDecimal("-100"), BigDecimal("60.00")),
        )

        val plan = service.computePlan()

        plan.saleCeiling.monthSold shouldBe BigDecimal.ZERO
    }

    "should exclude a day-trade-flagged order from the exemption meter" {
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
        // A buy already happened today, so the planned sell (150 > ideal 100) is a day trade —
        // taxed at 20% regardless, so its notional must not eat the R$20k exemption.
        coEvery { tradeRepository.fetchByAssetId(10) } returns listOf(
            Trade(2, 10, LocalDate.parse("2026-09-15"), BigDecimal("10"), BigDecimal("50.00")),
        )

        val plan = service.computePlan()

        plan.orders.single().dayTradeRisk shouldBe true
        plan.saleCeiling.monthSold shouldBe BigDecimal.ZERO
    }

    "should exclude a same-day buy+sell ledger pair from the exemption meter" {
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
            custodyQuantity = BigDecimal("100"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("100"))),
        )
        // A 100-share buy and a 100-share sell on the same day: the sell is the day-trade half of
        // the pair, so it never consumes the exemption.
        coEvery { tradeRepository.fetchByDateRange(any(), any()) } returns listOf(
            Trade(5, 10, LocalDate.parse("2026-09-10"), BigDecimal("100"), BigDecimal("40.00")),
            Trade(6, 10, LocalDate.parse("2026-09-10"), BigDecimal("-100"), BigDecimal("60.00")),
        )

        val plan = service.computePlan()

        plan.saleCeiling.monthSold shouldBe BigDecimal.ZERO
    }

    "should keep counting a swing sell (no same-day buy) toward the exemption meter" {
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
            custodyQuantity = BigDecimal("100"),
            balances = listOf(StrategyBalance(1, "Top", BigDecimal("100"))),
        )
        coEvery { tradeRepository.fetchByDateRange(any(), any()) } returns listOf(
            Trade(7, 10, LocalDate.parse("2026-09-05"), BigDecimal("200"), BigDecimal("40.00")),
            Trade(8, 10, LocalDate.parse("2026-09-12"), BigDecimal("-100"), BigDecimal("60.00")),
        )

        val plan = service.computePlan()

        plan.saleCeiling.monthSold shouldBe BigDecimal("6000.00")
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

    "should auto-apply a transfer under the configured threshold instead of leaving it pending" {
        val div = Strategy(id = 2, name = "Dividendos", assetClass = ACOES)
        coEvery { strategyService.fetchAll() } returns listOf(top, div)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("20000.00"),
            classes = listOf(ClassNode(ACOES, BigDecimal("1.0"), BigDecimal("20000.00"), BigDecimal("20000.00"))),
        )
        coEvery { strategyEditionService.fetchEditions(1) } returns listOf(
            edition(1, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
        )
        coEvery { strategyEditionService.fetchEditions(2) } returns listOf(
            edition(2, listOf(StrategyTarget("PETR4", BigDecimal("1.0")))),
        )
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { quoteGateway.getQuote(petr4) } returns Quote(BigDecimal("50.00"), today, BRAPI)
        // 2 shares * R$50 = R$100 notional
        coEvery { attributionService.summarize(10) } returns AttributionSummary(
            custodyQuantity = BigDecimal("200"),
            balances = listOf(
                StrategyBalance(1, "Top", BigDecimal("102")),
                StrategyBalance(2, "Dividendos", BigDecimal("98")),
            ),
        )
        coEvery { transferSettingsRepository.fetch() } returns TransferSettings(BigDecimal("200.00"))
        coEvery { transferProposalRepository.find(any(), 10, 1, 2) } returns null
        val saved = TransferProposal(
            id = 9,
            month = LocalDate.parse("2026-09-01"),
            listedAssetId = 10,
            ticker = "PETR4",
            fromStrategyId = 1,
            fromStrategyName = "Top",
            toStrategyId = 2,
            toStrategyName = "Dividendos",
            proposedQuantity = BigDecimal("2"),
            appliedQuantity = null,
            status = PENDENTE,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.save(any()) } returns saved
        coEvery { attributionService.recordMovement(any()) } returns mockk()
        coEvery { transferProposalRepository.decide(9, any(), any(), any()) } returns saved.copy(
            status = APLICADA,
            appliedQuantity = BigDecimal("2"),
        )

        val plan = service.computePlan()

        plan.transferProposals shouldBe emptyList()
        io.mockk.coVerify(exactly = 2) { attributionService.recordMovement(any()) }
        io.mockk.coVerify {
            transferProposalRepository.decide(
                9,
                APLICADA,
                BigDecimal("2"),
                any(),
            )
        }
    }

    "approveTransfer should apply the movements and mark the proposal APLICADA" {
        val pending = TransferProposal(
            id = 4,
            month = LocalDate.parse("2026-09-01"),
            listedAssetId = 10,
            ticker = "PETR4",
            fromStrategyId = 1,
            fromStrategyName = "Top",
            toStrategyId = 2,
            toStrategyName = "Dividendos",
            proposedQuantity = BigDecimal("9"),
            appliedQuantity = null,
            status = PENDENTE,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.fetchByMonth(LocalDate.parse("2026-09-01")) } returns listOf(pending)
        coEvery { attributionService.recordMovement(any()) } returns mockk()
        coEvery {
            transferProposalRepository.decide(4, APLICADA, BigDecimal("5"), any())
        } returns
            pending.copy(status = APLICADA, appliedQuantity = BigDecimal("5"))

        val result = service.approveTransfer(4, BigDecimal("5"))

        result.status shouldBe APLICADA
        io.mockk.coVerify(exactly = 2) { attributionService.recordMovement(any()) }
    }

    "approveTransfer should reject a quantity greater than proposed" {
        val pending = TransferProposal(
            id = 4,
            month = LocalDate.parse("2026-09-01"),
            listedAssetId = 10,
            ticker = "PETR4",
            fromStrategyId = 1,
            fromStrategyName = "Top",
            toStrategyId = 2,
            toStrategyName = "Dividendos",
            proposedQuantity = BigDecimal("9"),
            appliedQuantity = null,
            status = PENDENTE,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.fetchByMonth(LocalDate.parse("2026-09-01")) } returns listOf(pending)

        io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
            service.approveTransfer(4, BigDecimal("50"))
        }
    }

    "rejectTransfer should mark the proposal REJEITADA without touching attribution" {
        val pending = TransferProposal(
            id = 4,
            month = LocalDate.parse("2026-09-01"),
            listedAssetId = 10,
            ticker = "PETR4",
            fromStrategyId = 1,
            fromStrategyName = "Top",
            toStrategyId = 2,
            toStrategyName = "Dividendos",
            proposedQuantity = BigDecimal("9"),
            appliedQuantity = null,
            status = PENDENTE,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.fetchByMonth(LocalDate.parse("2026-09-01")) } returns listOf(pending)
        coEvery {
            transferProposalRepository.decide(4, REJEITADA, null, any())
        } returns
            pending.copy(status = REJEITADA)

        val result = service.rejectTransfer(4)

        result.status shouldBe REJEITADA
        io.mockk.coVerify(exactly = 0) { attributionService.recordMovement(any()) }
    }
})
