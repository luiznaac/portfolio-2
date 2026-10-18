package dev.agner.portfolio.persistence.strategy

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class StrategyRepository(
    private val clock: Clock,
) : IStrategyRepository {

    override suspend fun fetchAll(): List<Strategy> = transaction {
        StrategyEntity.all()
            .orderBy(StrategyTable.name to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchById(id: Int): Strategy? = transaction {
        StrategyEntity.findById(id)?.toModel()
    }

    override suspend fun save(creation: StrategyCreation): Strategy = transaction {
        StrategyEntity.new {
            name = creation.name
            assetClass = creation.assetClass
            weight = creation.weight
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }
}
