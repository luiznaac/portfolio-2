package dev.agner.portfolio.usecase.attribution

import dev.agner.portfolio.usecase.attribution.model.AttributionMovement
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import dev.agner.portfolio.usecase.attribution.model.AttributionReason.TRANSFERENCIA
import dev.agner.portfolio.usecase.attribution.model.AttributionSummary
import dev.agner.portfolio.usecase.attribution.model.StrategyBalance
import dev.agner.portfolio.usecase.attribution.repository.IAttributionRepository
import dev.agner.portfolio.usecase.commons.isZero
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.strategy.StrategyNotFoundException
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Attribution is a decision the user makes, never derived from the ledger — see the plan's
 * "atribuição por estratégia" decision. Custody (the fiscal truth) comes from replaying trades +
 * corporate actions, same as consolidation; attribution is the user's own log of movements on
 * top, with the two checked against each other rather than one driving the other.
 */
@Service
class AttributionService(
    private val attributionRepository: IAttributionRepository,
    private val strategyRepository: IStrategyRepository,
    private val tradeRepository: ITradeRepository,
    private val corporateActionRepository: ICorporateActionRepository,
    private val calculator: AveragePriceCalculator,
    private val transaction: ITransactionTemplate,
) {

    suspend fun recordMovement(creation: AttributionMovementCreation): AttributionMovement {
        val balances = fetchBalances(creation.listedAssetId)
        val currentBalance = balances[creation.strategyId] ?: BigDecimal.ZERO
        require(currentBalance + creation.quantity >= BigDecimal.ZERO) {
            "Attribution movement would leave strategy ${creation.strategyId} with a negative balance"
        }

        return attributionRepository.save(creation)
    }

    /**
     * Moves attributed quantity from one strategy to another for the same listed asset, as two
     * TRANSFERENCIA movements. Both saves run inside a single transaction: a failure between them
     * (a DB hiccup, or an unknown id surfacing late) rolls the pair back together instead of
     * leaving the from-strategy debited with no matching credit, which would silently break the
     * reconciliation invariant [summarize] is computed from.
     */
    suspend fun transferBetweenStrategies(
        listedAssetId: Int,
        fromStrategyId: Int,
        toStrategyId: Int,
        quantity: BigDecimal,
        date: LocalDate,
    ) {
        require(fromStrategyId != toStrategyId) {
            "A transfer must involve two different strategies (from $fromStrategyId to $toStrategyId)"
        }
        require(quantity > BigDecimal.ZERO) { "Transfer quantity must be positive" }
        strategyRepository.fetchById(fromStrategyId) ?: throw StrategyNotFoundException(fromStrategyId)
        strategyRepository.fetchById(toStrategyId) ?: throw StrategyNotFoundException(toStrategyId)

        transaction.execute {
            recordMovement(
                AttributionMovementCreation(
                    listedAssetId = listedAssetId,
                    strategyId = fromStrategyId,
                    date = date,
                    quantity = -quantity,
                    reason = TRANSFERENCIA,
                    note = "Transferência para a estratégia $toStrategyId",
                ),
            )
            recordMovement(
                AttributionMovementCreation(
                    listedAssetId = listedAssetId,
                    strategyId = toStrategyId,
                    date = date,
                    quantity = quantity,
                    reason = TRANSFERENCIA,
                    note = "Transferência da estratégia $fromStrategyId",
                ),
            )
        }
    }

    suspend fun summarize(assetId: Int): AttributionSummary {
        val custody = custodyQuantity(assetId)
        val balances = fetchBalances(assetId)
        val strategiesById = strategyRepository.fetchAll().associateBy { it.id }

        return AttributionSummary(
            custodyQuantity = custody,
            balances = balances
                .filterValues { !it.isZero() }
                .mapNotNull { (strategyId, quantity) ->
                    strategiesById[strategyId]?.let {
                        StrategyBalance(strategyId = strategyId, strategyName = it.name, quantity = quantity)
                    }
                },
        )
    }

    private suspend fun fetchBalances(assetId: Int): Map<Int, BigDecimal> =
        attributionRepository.fetchByAssetId(assetId)
            .groupBy { it.strategyId }
            .mapValues { (_, movements) -> movements.sumOf { it.quantity } }

    private suspend fun custodyQuantity(assetId: Int): BigDecimal {
        val trades = tradeRepository.fetchByAssetId(assetId)
        val corporateActions = corporateActionRepository.fetchByAssetId(assetId)
        return calculator.calculate(trades, corporateActions).position.quantity
    }
}
