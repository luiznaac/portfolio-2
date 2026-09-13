package dev.agner.portfolio.usecase.attribution

import dev.agner.portfolio.usecase.attribution.model.AttributionMovement
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import dev.agner.portfolio.usecase.attribution.model.AttributionSummary
import dev.agner.portfolio.usecase.attribution.model.StrategyBalance
import dev.agner.portfolio.usecase.attribution.repository.IAttributionRepository
import dev.agner.portfolio.usecase.commons.isZero
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Attribution is a decision the user makes, never derived from the ledger. Custody — the fiscal
 * truth — comes from replaying trades and corporate actions, the same way consolidation does;
 * attribution is the user's own log of movements on top. The two are checked against each other
 * rather than one driving the other, and a share is never apportioned between strategies
 * automatically.
 */
@Service
class AttributionService(
    private val attributionRepository: IAttributionRepository,
    private val strategyRepository: IStrategyRepository,
    private val tradeRepository: ITradeRepository,
    private val corporateActionRepository: ICorporateActionRepository,
    private val calculator: AveragePriceCalculator,
) {

    suspend fun recordMovement(assetId: Int, creation: AttributionMovementCreation): AttributionMovement {
        val balances = fetchBalances(assetId)
        val currentBalance = balances[creation.strategyId] ?: BigDecimal.ZERO
        require(currentBalance + creation.quantity >= BigDecimal.ZERO) {
            "Attribution movement would leave strategy ${creation.strategyId} with a negative balance"
        }

        return attributionRepository.save(assetId, creation)
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
