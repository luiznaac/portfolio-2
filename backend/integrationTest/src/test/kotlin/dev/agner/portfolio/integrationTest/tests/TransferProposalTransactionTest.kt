package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.allocation.model.AssetClass.ACOES
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import dev.agner.portfolio.usecase.attribution.model.AttributionReason
import dev.agner.portfolio.usecase.attribution.repository.IAttributionRepository
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.model.AssetKind.STOCK
import dev.agner.portfolio.usecase.listedasset.model.ListedAssetCreation
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.TransferProposalCreation
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.APLICADA
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
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

        val fromStrategy = strategyRepository.save(StrategyCreation(name = "Top", assetClass = ACOES))
        val toStrategy = strategyRepository.save(StrategyCreation(name = "Dividendos", assetClass = ACOES))
        val asset = listedAssetRepository.save(
            ListedAssetCreation(ticker = "PETR4", kind = STOCK, name = "Petrobras", b3Identifier = "PETROBRAS"),
        )
        attributionRepository.save(
            AttributionMovementCreation(
                listedAssetId = asset.id,
                strategyId = fromStrategy.id,
                date = LocalDate.parse("2026-09-01"),
                quantity = BigDecimal("100"),
                reason = AttributionReason.COMPRA,
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

        result.status shouldBe APLICADA
        val balances = attributionRepository.fetchByAssetId(asset.id)
            .groupBy { it.strategyId }
            .mapValues { (_, movements) -> movements.sumOf { it.quantity } }
        balances[fromStrategy.id] shouldBe BigDecimal("60.00000000")
        balances[toStrategy.id] shouldBe BigDecimal("40.00000000")
        proposalRepository.fetchByMonth(LocalDate.parse("2026-09-01")).single().status shouldBe APLICADA
    }

    "a failure on the status write rolls back both movements" {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        val strategyRepository = getBean<IStrategyRepository>()
        val listedAssetRepository = getBean<IListedAssetRepository>()
        val attributionRepository = getBean<IAttributionRepository>()
        val proposalRepository = getBean<ITransferProposalRepository>()
        val transaction = getBean<ITransactionTemplate>()

        val fromStrategy = strategyRepository.save(StrategyCreation(name = "Top", assetClass = ACOES))
        val toStrategy = strategyRepository.save(StrategyCreation(name = "Dividendos", assetClass = ACOES))
        val asset = listedAssetRepository.save(
            ListedAssetCreation(ticker = "PETR4", kind = STOCK, name = "Petrobras", b3Identifier = "PETROBRAS"),
        )

        shouldThrow<IllegalArgumentException> {
            transaction.execute {
                attributionRepository.save(
                    AttributionMovementCreation(
                        listedAssetId = asset.id,
                        strategyId = fromStrategy.id,
                        date = LocalDate.parse("2026-09-15"),
                        quantity = BigDecimal("-40"),
                        reason = AttributionReason.TRANSFERENCIA,
                    ),
                )
                attributionRepository.save(
                    AttributionMovementCreation(
                        listedAssetId = asset.id,
                        strategyId = toStrategy.id,
                        date = LocalDate.parse("2026-09-15"),
                        quantity = BigDecimal("40"),
                        reason = AttributionReason.TRANSFERENCIA,
                    ),
                )
                // The status write targets an unknown proposal; the whole execute must roll back.
                proposalRepository.decide(999_999, APLICADA, BigDecimal("40"), LocalDateTime.now(ClockMock.clock))
            }
        }

        attributionRepository.fetchByAssetId(asset.id) shouldBe emptyList()
    }

    "saving the same pairing twice returns the existing proposal and leaves one row" {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        val strategyRepository = getBean<IStrategyRepository>()
        val listedAssetRepository = getBean<IListedAssetRepository>()
        val proposalRepository = getBean<ITransferProposalRepository>()

        val fromStrategy = strategyRepository.save(StrategyCreation(name = "Top", assetClass = ACOES))
        val toStrategy = strategyRepository.save(StrategyCreation(name = "Dividendos", assetClass = ACOES))
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
})
