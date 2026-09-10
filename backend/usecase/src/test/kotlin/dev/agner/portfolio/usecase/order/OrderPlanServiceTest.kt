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
import dev.agner.portfolio.usecase.order.model.OrderKind.COMPRAR
import dev.agner.portfolio.usecase.order.model.OrderKind.VENDER
import dev.agner.portfolio.usecase.order.model.TransferProposal
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
        coEvery { tradeRepository.fetchAll() } returns emptyList()
        coEvery { transferSettingsRepository.fetch() } returns TransferSettings(BigDecimal.ZERO)
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
        petrOrder.kind shouldBe VENDER
        petrOrder.quantity shouldBe BigDecimal("50")
        petrOrder.notional shouldBe BigDecimal("2500.00")

        val valeOrder = plan.orders.single { it.ticker == "VALE3" }
        valeOrder.kind shouldBe COMPRAR
        valeOrder.quantity shouldBe BigDecimal("100")

        plan.transferProposals shouldBe emptyList()
        plan.saleCeiling.monthSold shouldBe BigDecimal("2500.00")
        plan.saleCeiling.exceeded shouldBe false
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
        plan.saleCeiling.exceeded shouldBe true
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
