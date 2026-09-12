package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.allocation.model.AssetClass.ACOES
import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import dev.agner.portfolio.usecase.attribution.model.AttributionReason
import dev.agner.portfolio.usecase.attribution.repository.IAttributionRepository
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.model.AssetKind.STOCK
import dev.agner.portfolio.usecase.listedasset.model.ListedAssetCreation
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.strategy.StrategyNotFoundException
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant

@IntegrationTest
class TransferTransactionTest : StringSpec({

    "applying a transfer persists both movements in one transaction" {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        val strategyRepository = getBean<IStrategyRepository>()
        val listedAssetRepository = getBean<IListedAssetRepository>()
        val attributionRepository = getBean<IAttributionRepository>()
        val attributionService = getBean<AttributionService>()

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

        attributionService.transferBetweenStrategies(
            listedAssetId = asset.id,
            fromStrategyId = fromStrategy.id,
            toStrategyId = toStrategy.id,
            quantity = BigDecimal("40"),
            date = LocalDate.parse("2026-09-15"),
        )

        val balances = attributionRepository.fetchByAssetId(asset.id)
            .groupBy { it.strategyId }
            .mapValues { (_, movements) -> movements.sumOf { it.quantity } }
        balances[fromStrategy.id] shouldBe BigDecimal("60.00000000")
        balances[toStrategy.id] shouldBe BigDecimal("40.00000000")
    }

    "an unknown to-strategy returns StrategyNotFoundException and writes nothing" {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        val strategyRepository = getBean<IStrategyRepository>()
        val listedAssetRepository = getBean<IListedAssetRepository>()
        val attributionRepository = getBean<IAttributionRepository>()
        val attributionService = getBean<AttributionService>()

        val fromStrategy = strategyRepository.save(StrategyCreation(name = "Top", assetClass = ACOES))
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

        shouldThrow<StrategyNotFoundException> {
            attributionService.transferBetweenStrategies(
                listedAssetId = asset.id,
                fromStrategyId = fromStrategy.id,
                toStrategyId = 999_999,
                quantity = BigDecimal("40"),
                date = LocalDate.parse("2026-09-15"),
            )
        }

        val balances = attributionRepository.fetchByAssetId(asset.id)
            .groupBy { it.strategyId }
            .mapValues { (_, movements) -> movements.sumOf { it.quantity } }
        balances[fromStrategy.id] shouldBe BigDecimal("100.00000000")
    }

    "a failed second save rolls back the first movement" {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        val strategyRepository = getBean<IStrategyRepository>()
        val listedAssetRepository = getBean<IListedAssetRepository>()
        val attributionRepository = getBean<IAttributionRepository>()
        val transaction = getBean<ITransactionTemplate>()

        val strategy = strategyRepository.save(StrategyCreation(name = "Top", assetClass = ACOES))
        val asset = listedAssetRepository.save(
            ListedAssetCreation(ticker = "PETR4", kind = STOCK, name = "Petrobras", b3Identifier = "PETROBRAS"),
        )

        shouldThrow<IllegalArgumentException> {
            transaction.execute {
                attributionRepository.save(
                    AttributionMovementCreation(
                        listedAssetId = asset.id,
                        strategyId = strategy.id,
                        date = LocalDate.parse("2026-09-01"),
                        quantity = BigDecimal("100"),
                        reason = AttributionReason.COMPRA,
                    ),
                )
                // The second save targets an unknown strategy: the FK must blow up inside the
                // same transaction and take the first movement down with it.
                attributionRepository.save(
                    AttributionMovementCreation(
                        listedAssetId = asset.id,
                        strategyId = 999_999,
                        date = LocalDate.parse("2026-09-01"),
                        quantity = BigDecimal("40"),
                        reason = AttributionReason.COMPRA,
                    ),
                )
            }
        }

        attributionRepository.fetchByAssetId(asset.id) shouldBe emptyList()
    }
})
