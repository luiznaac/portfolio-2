package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.allocation.model.AllocationPlan
import dev.agner.portfolio.usecase.allocation.model.AssetClass.STOCKS
import dev.agner.portfolio.usecase.allocation.model.ClassNode
import dev.agner.portfolio.usecase.attribution.model.AttributionSummary
import dev.agner.portfolio.usecase.attribution.model.StrategyBalance
import dev.agner.portfolio.usecase.listedasset.model.Quote
import dev.agner.portfolio.usecase.listedasset.model.QuoteSource.BRAPI
import dev.agner.portfolio.usecase.order.model.OrderKind.BUY
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.APPLIED
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.PENDING
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.REJECTED
import dev.agner.portfolio.usecase.order.model.TransferSettings
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.model.StrategyWeight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class OrderPlanTransferTest : StringSpec({
    val env = orderPlanTestEnv()
    val strategyService = env.strategyService
    val strategyWeightRepository = env.strategyWeightRepository
    val strategyEditionService = env.strategyEditionService
    val allocationService = env.allocationService
    val attributionService = env.attributionService
    val listedAssetRepository = env.listedAssetRepository
    val quoteGateway = env.quoteGateway
    val transferProposalRepository = env.transferProposalRepository
    val transferSettingsRepository = env.transferSettingsRepository
    val service = env.service

    beforeTest { env.stubDefaults() }

    "should create a new pending transfer proposal for a fresh excess/shortage pairing" {
        val div = Strategy(id = 2, name = "Dividendos", assetClass = STOCKS)
        coEvery { strategyService.fetchAll() } returns listOf(top, div)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("20000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("20000.00"), BigDecimal("20000.00"))),
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
            status = PENDING,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.save(any()) } returns saved

        val plan = service.refreshPlan()

        plan.transferProposals shouldBe listOf(saved)
        // A pairing the matcher still returns is live: it must not be auto-rejected.
        io.mockk.coVerify(exactly = 0) { transferProposalRepository.decide(any(), any(), any(), any()) }
    }

    "should auto-reject a pending proposal whose pairing no longer matches" {
        coEvery { strategyService.fetchAll() } returns emptyList()
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns emptyList()
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal.ZERO,
            classes = emptyList(),
        )
        coEvery { listedAssetRepository.fetchAll() } returns emptyList()
        // A proposal left over from a pairing that has since disappeared (price/capital change or
        // manual attribution movement): the matcher will not return it, so it must be expired.
        val stranded = proposal(2, "Dividendos", "50")
        coEvery { transferProposalRepository.fetchByMonth(LocalDate.parse("2026-09-01")) } returns
            listOf(stranded)
        coEvery { transferProposalRepository.decide(any(), any(), any(), any()) } returns
            stranded.copy(status = REJECTED)

        val plan = service.refreshPlan()

        plan.transferProposals shouldBe emptyList()
        io.mockk.coVerify(exactly = 1) {
            transferProposalRepository.decide(2, REJECTED, null, any())
        }
    }

    "should keep a pending proposal whose quantity is numerically equal but scaled differently" {
        val div = Strategy(id = 2, name = "Dividendos", assetClass = STOCKS)
        coEvery { strategyService.fetchAll() } returns listOf(top, div)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("20000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("20000.00"), BigDecimal("20000.00"))),
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
            status = PENDING,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.find(any(), 10, 1, 2) } returns existing

        val plan = service.refreshPlan()

        plan.transferProposals shouldBe listOf(existing)
        io.mockk.coVerify(exactly = 0) { transferProposalRepository.updateProposedQuantity(any(), any()) }
    }

    "should not resurrect a transfer proposal rejected earlier this month" {
        val div = Strategy(id = 2, name = "Dividendos", assetClass = STOCKS)
        coEvery { strategyService.fetchAll() } returns listOf(top, div)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("20000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("20000.00"), BigDecimal("20000.00"))),
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
            status = REJECTED,
            decidedAt = kotlinx.datetime.LocalDateTime.parse("2026-09-10T10:00:00"),
        )

        val plan = service.refreshPlan()

        plan.transferProposals shouldBe emptyList()
    }

    "should propose a free transfer when the attribution is off but custody is already right" {
        val dividendos = Strategy(id = 2, name = "Dividendos", assetClass = STOCKS)
        val smallCaps = Strategy(id = 3, name = "Small Caps", assetClass = STOCKS)
        coEvery { strategyService.fetchAll() } returns listOf(top, dividendos, smallCaps)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.5"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.25"), LocalDate.parse("2026-01-01")),
            StrategyWeight(3, 3, BigDecimal("0.25"), LocalDate.parse("2026-01-01")),
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

        val plan = service.refreshPlan()

        plan.orders shouldBe emptyList()
        plan.transferProposals.associate { it.toStrategyId to it.proposedQuantity } shouldBe mapOf(
            2 to BigDecimal("50"),
            3 to BigDecimal("50"),
        )
        plan.transferProposals.all { it.fromStrategyId == 1 } shouldBe true
    }

    "should emit a transfer proposal and a residual net order together for the same ticker" {
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

        val plan = service.refreshPlan()

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

    "should auto-apply a transfer under the configured threshold instead of leaving it pending" {
        val div = Strategy(id = 2, name = "Dividendos", assetClass = STOCKS)
        coEvery { strategyService.fetchAll() } returns listOf(top, div)
        coEvery { strategyWeightRepository.fetchCurrent(any()) } returns listOf(
            StrategyWeight(1, 1, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
            StrategyWeight(2, 2, BigDecimal("0.2500"), LocalDate.parse("2026-01-01")),
        )
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("20000.00"),
            classes = listOf(ClassNode(STOCKS, BigDecimal("1.0"), BigDecimal("20000.00"), BigDecimal("20000.00"))),
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
            status = PENDING,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.save(any()) } returns saved
        coEvery { attributionService.recordMovement(any(), any()) } returns mockk()
        coEvery { transferProposalRepository.decide(9, any(), any(), any()) } returns saved.copy(
            status = APPLIED,
            appliedQuantity = BigDecimal("2"),
        )

        val plan = service.refreshPlan()

        plan.transferProposals shouldBe emptyList()
        io.mockk.coVerify(exactly = 2) { attributionService.recordMovement(any(), any()) }
        io.mockk.coVerify {
            transferProposalRepository.decide(
                9,
                APPLIED,
                BigDecimal("2"),
                any(),
            )
        }
    }

    "approveTransfer should apply the movements and mark the proposal APPLIED" {
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
            status = PENDING,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.fetchById(4) } returns pending
        coEvery { attributionService.recordMovement(any(), any()) } returns mockk()
        coEvery {
            transferProposalRepository.decide(4, APPLIED, BigDecimal("5"), any())
        } returns
            pending.copy(status = APPLIED, appliedQuantity = BigDecimal("5"))

        val result = service.approveTransfer(4, BigDecimal("5"))

        result.status shouldBe APPLIED
        io.mockk.coVerify(exactly = 2) { attributionService.recordMovement(any(), any()) }
    }

    "approveTransfer should attempt both movements before a failing decide, inside one transaction" {
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
            status = PENDING,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.fetchById(4) } returns pending
        coEvery { attributionService.recordMovement(any(), any()) } returns mockk()
        coEvery { transferProposalRepository.decide(4, APPLIED, BigDecimal("5"), any()) } throws
            RuntimeException("decide fails")

        io.kotest.assertions.throwables.shouldThrow<RuntimeException> {
            service.approveTransfer(4, BigDecimal("5"))
        }

        // Both movements were attempted before decide blew up, so they share the execute block
        // that decide's failure rolls back with it.
        io.mockk.coVerify(exactly = 2) { attributionService.recordMovement(any(), any()) }
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
            status = PENDING,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.fetchById(4) } returns pending

        io.kotest.assertions.throwables.shouldThrow<InvalidTransferQuantityException> {
            service.approveTransfer(4, BigDecimal("50"))
        }
    }

    "approveTransfer should surface a domain error for an unknown proposal" {
        coEvery { transferProposalRepository.fetchById(99) } returns null

        io.kotest.assertions.throwables.shouldThrow<TransferProposalNotFoundException> {
            service.approveTransfer(99, BigDecimal("5"))
        }
    }

    "approveTransfer should surface a domain error for a non-pending proposal" {
        val applied = TransferProposal(
            id = 4,
            month = LocalDate.parse("2026-09-01"),
            listedAssetId = 10,
            ticker = "PETR4",
            fromStrategyId = 1,
            fromStrategyName = "Top",
            toStrategyId = 2,
            toStrategyName = "Dividendos",
            proposedQuantity = BigDecimal("9"),
            appliedQuantity = BigDecimal("9"),
            status = APPLIED,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.fetchById(4) } returns applied

        io.kotest.assertions.throwables.shouldThrow<TransferProposalNotPendingException> {
            service.approveTransfer(4, BigDecimal("5"))
        }
    }

    "rejectTransfer should mark the proposal REJECTED without touching attribution" {
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
            status = PENDING,
            decidedAt = null,
        )
        coEvery { transferProposalRepository.fetchById(4) } returns pending
        coEvery {
            transferProposalRepository.decide(4, REJECTED, null, any())
        } returns
            pending.copy(status = REJECTED)

        val result = service.rejectTransfer(4)

        result.status shouldBe REJECTED
        io.mockk.coVerify(exactly = 0) { attributionService.recordMovement(any(), any()) }
    }
})
