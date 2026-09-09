package dev.agner.portfolio.persistence.strategy

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.strategy.model.StrategyEdition
import dev.agner.portfolio.usecase.strategy.model.StrategyEditionCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.repository.IStrategyEditionRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class StrategyEditionRepository(
    private val clock: Clock,
) : IStrategyEditionRepository {

    override suspend fun fetchByStrategyId(strategyId: Int): List<StrategyEdition> = transaction {
        StrategyEditionEntity.find { StrategyEditionTable.strategy eq strategyId }
            .orderBy(StrategyEditionTable.referenceDate to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun save(creation: StrategyEditionCreation): StrategyEdition = transaction {
        val entity = StrategyEditionEntity.new {
            strategy = StrategyEntity.findById(creation.strategyId)
                ?: throw IllegalArgumentException("Strategy with ID ${creation.strategyId} not found")
            referenceDate = creation.referenceDate
            changesText = creation.changesText
            createdAt = LocalDateTime.now(clock)
        }

        StrategyTargetTable.batchInsert(creation.targets) { target ->
            this[StrategyTargetTable.strategyEdition] = entity.id
            this[StrategyTargetTable.ticker] = target.ticker
            this[StrategyTargetTable.weight] = target.weight
            this[StrategyTargetTable.rating] = target.rating
            this[StrategyTargetTable.targetPrice] = target.targetPrice
        }

        entity.toModel(creation.targets)
    }
}

private fun StrategyEditionEntity.toModel(): StrategyEdition {
    val targets = StrategyTargetEntity.find { StrategyTargetTable.strategyEdition eq id }
        .map { it.toModel() }
    return toModel(targets)
}

private fun StrategyEditionEntity.toModel(targets: List<StrategyTarget>) = StrategyEdition(
    id = id.value,
    strategyId = strategy.id.value,
    referenceDate = referenceDate,
    changesText = changesText,
    targets = targets,
)

private fun StrategyTargetEntity.toModel() = StrategyTarget(
    ticker = ticker,
    weight = weight,
    rating = rating,
    targetPrice = targetPrice,
)
