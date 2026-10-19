package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.persistence.strategy.StrategyEntity
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component

@Component
class AttributionRepository : IAttributionRepository {

    override suspend fun fetchBy(ticker: String): List<Attribution> = transaction {
        AttributionEntity.find { AttributionTable.ticker eq ticker }
            .orderBy(AttributionTable.strategyId to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun save(attribution: Attribution) {
        transaction {
            val existing = AttributionEntity.find {
                (AttributionTable.ticker eq attribution.ticker) and
                    (AttributionTable.strategyId eq attribution.strategyId)
            }.firstOrNull()

            if (existing == null) {
                AttributionEntity.new {
                    ticker = attribution.ticker
                    strategy = StrategyEntity[attribution.strategyId]
                    quantity = attribution.quantity
                }
            } else {
                existing.quantity = attribution.quantity
            }
        }
    }

    override suspend fun delete(ticker: String, strategyId: Int) {
        transaction {
            AttributionEntity.find {
                (AttributionTable.ticker eq ticker) and
                    (AttributionTable.strategyId eq strategyId)
            }.forEach { it.delete() }
        }
    }
}
