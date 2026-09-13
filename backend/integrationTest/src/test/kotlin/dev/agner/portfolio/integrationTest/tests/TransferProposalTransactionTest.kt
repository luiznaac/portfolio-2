package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.allocation.model.AssetClass.STOCKS
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import dev.agner.portfolio.usecase.attribution.model.AttributionReason
import dev.agner.portfolio.usecase.attribution.repository.IAttributionRepository
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.model.AssetKind.STOCK
import dev.agner.portfolio.usecase.listedasset.model.ListedAssetCreation
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.TransferProposalNotPendingException
import dev.agner.portfolio.usecase.order.model.TransferProposalCreation
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.APPLIED
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal
import java.time.Instant

// The real-database counterpart of the fake inline transaction in OrderPlanServiceTest: it proves
// that approving a transfer commits the two attribution movements and the status change together,
// and that a final failure rolls all three back. The old TransferTransactionTest covered
// AttributionService.transferBetweenStrategies, which the lifecycle no longer uses.
@IntegrationTest
class TransferProposalTransactionTest : StringSpec({

    "approving a proposal commits both movements and the status together" {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        val strategyRepository = getBean<IStrategyRepository>()
        val listedAssetRepository = getBean<IListedAssetRepository>()
        val attributionRepository = getBean<IAttributionRepository>()
        val proposalRepository = getBean<ITransferProposalRepository>()
        val planService = getBean<OrderPlanService>()

        val fromStrategy = strategyRepository.save(StrategyCreation(name = "Top", assetClass = STOCKS))
        val toStrategy = strategyRepository.save(StrategyCreation(name = "Dividendos", assetClass = STOCKS))
        val asset = listedAssetRepository.save(
            ListedAssetCreation(ticker = "PETR4", kind = STOCK, name = "Petrobras", b3Identifier = "PETROBRAS"),
        )
        attributionRepository.save(
            asset.id,
            AttributionMovementCreation(
                strategyId = fromStrategy.id,
                date = LocalDate.parse("2026-09-01"),
                quantity = BigDecimal("100"),
                reason = AttributionReason.BUY,
            ),
        )
        val proposal = proposalRepository.save(
            TransferProposalCreation(
                month = LocalDate.parse("2026-09-01"),
                listedAssetId = asset.id,
                ticker = "PETR4",
                fromStrategyId = fromStrategy.id,
                fromStrategyName = fromStrategy.name,
                toStrategyId = toStrategy.id,
                toStrategyName = toStrategy.name,
                proposedQuantity = BigDecimal("40"),
            ),
        )

        val result = planService.approveTransfer(proposal.id, null)

        result.status shouldBe APPLIED
        val balances = attributionRepository.fetchByAssetId(asset.id)
            .groupBy { it.strategyId }
            .mapValues { (_, movements) -> movements.sumOf { it.quantity } }
        balances[fromStrategy.id] shouldBe BigDecimal("60.00000000")
        balances[toStrategy.id] shouldBe BigDecimal("40.00000000")
        proposalRepository.fetchByMonth(LocalDate.parse("2026-09-01")).single().status shouldBe APPLIED
    }

    "a failure on the status write rolls back both movements" {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        val strategyRepository = getBean<IStrategyRepository>()
        val listedAssetRepository = getBean<IListedAssetRepository>()
        val attributionRepository = getBean<IAttributionRepository>()
        val proposalRepository = getBean<ITransferProposalRepository>()
        val transaction = getBean<ITransactionTemplate>()

        val fromStrategy = strategyRepository.save(StrategyCreation(name = "Top", assetClass = STOCKS))
        val toStrategy = strategyRepository.save(StrategyCreation(name = "Dividendos", assetClass = STOCKS))
        val asset = listedAssetRepository.save(
            ListedAssetCreation(ticker = "PETR4", kind = STOCK, name = "Petrobras", b3Identifier = "PETROBRAS"),
        )

        shouldThrow<IllegalArgumentException> {
            transaction.execute {
                attributionRepository.save(
                    asset.id,
                    AttributionMovementCreation(
                        strategyId = fromStrategy.id,
                        date = LocalDate.parse("2026-09-15"),
                        quantity = BigDecimal("-40"),
                        reason = AttributionReason.TRANSFER,
                    ),
                )
                attributionRepository.save(
                    asset.id,
                    AttributionMovementCreation(
                        strategyId = toStrategy.id,
                        date = LocalDate.parse("2026-09-15"),
                        quantity = BigDecimal("40"),
                        reason = AttributionReason.TRANSFER,
                    ),
                )
                // The status write targets an unknown proposal; the whole execute must roll back.
                proposalRepository.decide(999_999, APPLIED, BigDecimal("40"), LocalDateTime.now(ClockMock.clock))
            }
        }

        attributionRepository.fetchByAssetId(asset.id) shouldBe emptyList()
    }

    "saving the same pairing twice returns the existing proposal and leaves one row" {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        val strategyRepository = getBean<IStrategyRepository>()
        val listedAssetRepository = getBean<IListedAssetRepository>()
        val proposalRepository = getBean<ITransferProposalRepository>()

        val fromStrategy = strategyRepository.save(StrategyCreation(name = "Top", assetClass = STOCKS))
        val toStrategy = strategyRepository.save(StrategyCreation(name = "Dividendos", assetClass = STOCKS))
        val asset = listedAssetRepository.save(
            ListedAssetCreation(ticker = "PETR4", kind = STOCK, name = "Petrobras", b3Identifier = "PETROBRAS"),
        )
        val creation = TransferProposalCreation(
            month = LocalDate.parse("2026-09-01"),
            listedAssetId = asset.id,
            ticker = "PETR4",
            fromStrategyId = fromStrategy.id,
            fromStrategyName = fromStrategy.name,
            toStrategyId = toStrategy.id,
            toStrategyName = toStrategy.name,
            proposedQuantity = BigDecimal("40"),
        )

        val first = proposalRepository.save(creation)
        val second = proposalRepository.save(creation)

        second.id shouldBe first.id
        proposalRepository.fetchByMonth(LocalDate.parse("2026-09-01")).size shouldBe 1
    }

    "deciding an already decided proposal is rejected and changes nothing" {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        val strategyRepository = getBean<IStrategyRepository>()
        val listedAssetRepository = getBean<IListedAssetRepository>()
        val attributionRepository = getBean<IAttributionRepository>()
        val proposalRepository = getBean<ITransferProposalRepository>()

        val fromStrategy = strategyRepository.save(StrategyCreation(name = "Top", assetClass = STOCKS))
        val toStrategy = strategyRepository.save(StrategyCreation(name = "Dividendos", assetClass = STOCKS))
        val asset = listedAssetRepository.save(
            ListedAssetCreation(ticker = "PETR4", kind = STOCK, name = "Petrobras", b3Identifier = "PETROBRAS"),
        )
        attributionRepository.save(
            asset.id,
            AttributionMovementCreation(
                strategyId = fromStrategy.id,
                date = LocalDate.parse("2026-09-01"),
                quantity = BigDecimal("100"),
                reason = AttributionReason.BUY,
            ),
        )
        val proposal = proposalRepository.save(
            TransferProposalCreation(
                month = LocalDate.parse("2026-09-01"),
                listedAssetId = asset.id,
                ticker = "PETR4",
                fromStrategyId = fromStrategy.id,
                fromStrategyName = fromStrategy.name,
                toStrategyId = toStrategy.id,
                toStrategyName = toStrategy.name,
                proposedQuantity = BigDecimal("40"),
            ),
        )

        proposalRepository.decide(proposal.id, APPLIED, BigDecimal("40"), LocalDateTime.now(ClockMock.clock))

        shouldThrow<TransferProposalNotPendingException> {
            proposalRepository.decide(proposal.id, APPLIED, BigDecimal("20"), LocalDateTime.now(ClockMock.clock))
        }

        val stored = proposalRepository.fetchById(proposal.id)
        stored?.status shouldBe APPLIED
        stored?.appliedQuantity shouldBe BigDecimal("40.00000000")
        attributionRepository.fetchByAssetId(asset.id).map { it.quantity } shouldBe
            listOf(BigDecimal("100.00000000"))
    }

    "two concurrent approvals apply the proposal exactly once" {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        val strategyRepository = getBean<IStrategyRepository>()
        val listedAssetRepository = getBean<IListedAssetRepository>()
        val attributionRepository = getBean<IAttributionRepository>()
        val proposalRepository = getBean<ITransferProposalRepository>()
        val planService = getBean<OrderPlanService>()

        val fromStrategy = strategyRepository.save(StrategyCreation(name = "Top", assetClass = STOCKS))
        val toStrategy = strategyRepository.save(StrategyCreation(name = "Dividendos", assetClass = STOCKS))
        val asset = listedAssetRepository.save(
            ListedAssetCreation(ticker = "PETR4", kind = STOCK, name = "Petrobras", b3Identifier = "PETROBRAS"),
        )
        attributionRepository.save(
            asset.id,
            AttributionMovementCreation(
                strategyId = fromStrategy.id,
                date = LocalDate.parse("2026-09-01"),
                quantity = BigDecimal("100"),
                reason = AttributionReason.BUY,
            ),
        )
        val proposal = proposalRepository.save(
            TransferProposalCreation(
                month = LocalDate.parse("2026-09-01"),
                listedAssetId = asset.id,
                ticker = "PETR4",
                fromStrategyId = fromStrategy.id,
                fromStrategyName = fromStrategy.name,
                toStrategyId = toStrategy.id,
                toStrategyName = toStrategy.name,
                proposedQuantity = BigDecimal("40"),
            ),
        )

        val outcomes = runBlocking {
            listOf(
                async(Dispatchers.IO) { runCatching { planService.approveTransfer(proposal.id, null) } },
                async(Dispatchers.IO) { runCatching { planService.approveTransfer(proposal.id, null) } },
            ).awaitAll()
        }

        outcomes.count { it.isSuccess } shouldBe 1
        outcomes.mapNotNull { it.exceptionOrNull() }.single()
            .shouldBeInstanceOf<TransferProposalNotPendingException>()

        val movements = attributionRepository.fetchByAssetId(asset.id)
        movements.filter { it.reason == AttributionReason.TRANSFER }.size shouldBe 2
        val balances = movements.groupBy { it.strategyId }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
        balances[fromStrategy.id] shouldBe BigDecimal("60.00000000")
        balances[toStrategy.id] shouldBe BigDecimal("40.00000000")

        val stored = proposalRepository.fetchById(proposal.id)
        stored?.status shouldBe APPLIED
        stored?.appliedQuantity shouldBe BigDecimal("40.00000000")
    }
})
