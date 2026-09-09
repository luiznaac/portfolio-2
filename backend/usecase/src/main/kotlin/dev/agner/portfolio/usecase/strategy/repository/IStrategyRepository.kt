package dev.agner.portfolio.usecase.strategy.repository

import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation

interface IStrategyRepository {

    suspend fun fetchAll(): List<Strategy>

    suspend fun fetchById(id: Int): Strategy?

    suspend fun save(creation: StrategyCreation): Strategy
}
