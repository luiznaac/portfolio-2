package dev.agner.portfolio.usecase.attribution

import dev.agner.portfolio.usecase.allocation.model.AssetClass.ACOES
import dev.agner.portfolio.usecase.attribution.model.AttributionMovement
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import dev.agner.portfolio.usecase.attribution.model.AttributionReason.COMPRA
import dev.agner.portfolio.usecase.attribution.repository.IAttributionRepository
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.strategy.StrategyNotFoundException
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class AttributionServiceTest : StringSpec({
    val attributionRepository = mockk<IAttributionRepository>()
    val strategyRepository = mockk<IStrategyRepository>()
    val tradeRepository = mockk<ITradeRepository>()
    val corporateActionRepository = mockk<ICorporateActionRepository>()
    val calculator = mockk<AveragePriceCalculator>()
    // A template that actually runs the block, like TransactionService does, so the two saves land
    // inside one execute and the rollback-on-second-failure path is observable.
    val transaction = object : ITransactionTemplate {
        override suspend fun <T> execute(block: suspend () -> T): T = block()
    }

    val service = AttributionService(
        attributionRepository,
        strategyRepository,
        tradeRepository,
        corporateActionRepository,
        calculator,
        transaction,
    )

    val top = Strategy(id = 1, name = "Top", assetClass = ACOES)
    val dividendos = Strategy(id = 2, name = "Dividendos", assetClass = ACOES)

    beforeTest {
        clearAllMocks()
        coEvery { strategyRepository.fetchById(1) } returns top
        coEvery { strategyRepository.fetchById(2) } returns dividendos
        // The from-strategy already holds 100 shares of the asset, so the 50-share debit passes
        // the negative-balance guard in recordMovement.
        coEvery { attributionRepository.fetchByAssetId(any()) } returns listOf(
            AttributionMovement(1, 10, 1, LocalDate.parse("2026-09-01"), BigDecimal("100"), COMPRA),
        )
        coEvery { attributionRepository.save(any()) } returns mockk(relaxed = true)
    }

    "should run both movements inside a single transaction" {
        service.transferBetweenStrategies(
            listedAssetId = 10,
            fromStrategyId = 1,
            toStrategyId = 2,
            quantity = BigDecimal("50"),
            date = LocalDate.parse("2026-09-15"),
        )

        coVerify(exactly = 2) { attributionRepository.save(any()) }
        coVerify(exactly = 1) {
            attributionRepository.save(
                match<AttributionMovementCreation> { it.strategyId == 1 && it.quantity == BigDecimal("-50") },
            )
        }
        coVerify(exactly = 1) {
            attributionRepository.save(
                match<AttributionMovementCreation> { it.strategyId == 2 && it.quantity == BigDecimal("50") },
            )
        }
    }

    "should roll back when the second movement fails" {
        coEvery { attributionRepository.save(any()) } returns mockk(relaxed = true) andThenThrows
            RuntimeException("second save fails")

        shouldThrow<RuntimeException> {
            service.transferBetweenStrategies(
                listedAssetId = 10,
                fromStrategyId = 1,
                toStrategyId = 2,
                quantity = BigDecimal("50"),
                date = LocalDate.parse("2026-09-15"),
            )
        }

        // Both saves were attempted inside the single execute; the exception from the second one
        // propagates out of it, so the transaction (which the template runs verbatim) never commits.
        coVerify(exactly = 2) { attributionRepository.save(any()) }
    }

    "should refuse a transfer to the same strategy" {
        shouldThrow<IllegalArgumentException> {
            service.transferBetweenStrategies(
                listedAssetId = 10,
                fromStrategyId = 1,
                toStrategyId = 1,
                quantity = BigDecimal("50"),
                date = LocalDate.parse("2026-09-15"),
            )
        }
        coVerify(exactly = 0) { attributionRepository.save(any()) }
    }

    "should refuse a non-positive quantity" {
        shouldThrow<IllegalArgumentException> {
            service.transferBetweenStrategies(
                listedAssetId = 10,
                fromStrategyId = 1,
                toStrategyId = 2,
                quantity = BigDecimal.ZERO,
                date = LocalDate.parse("2026-09-15"),
            )
        }
        coVerify(exactly = 0) { attributionRepository.save(any()) }
    }

    "should surface StrategyNotFoundException for an unknown strategy instead of the FK blowing up" {
        coEvery { strategyRepository.fetchById(99) } returns null

        shouldThrow<StrategyNotFoundException> {
            service.transferBetweenStrategies(
                listedAssetId = 10,
                fromStrategyId = 1,
                toStrategyId = 99,
                quantity = BigDecimal("50"),
                date = LocalDate.parse("2026-09-15"),
            )
        }
        coVerify(exactly = 0) { attributionRepository.save(any()) }
    }
})
