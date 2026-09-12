package dev.agner.portfolio.persistence.strategy

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.strategy.model.StrategyWeightCreation
import dev.agner.portfolio.usecase.strategy.repository.IStrategyWeightRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class StrategyWeightRepository(
    private val clock: Clock,
) : IStrategyWeightRepository {

    override suspend fun fetchAll() = transaction {
        StrategyWeightEntity.all()
            .orderBy(StrategyWeightTable.effectiveFrom to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchCurrent(date: LocalDate) = transaction {
        StrategyWeightEntity.find { StrategyWeightTable.effectiveFrom lessEq date }
            .orderBy(StrategyWeightTable.effectiveFrom to SortOrder.ASC)
            .map { it.toModel() }
            .groupBy { it.strategyId }
            .values
            .map { it.last() }
    }

    override suspend fun save(strategyId: Int, creation: StrategyWeightCreation) = transaction {
        StrategyWeightEntity.new {
            strategy = StrategyEntity.findById(strategyId)
                ?: throw IllegalArgumentException("Strategy with ID $strategyId not found")
            weight = creation.weight
            effectiveFrom = creation.effectiveFrom
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }
}
