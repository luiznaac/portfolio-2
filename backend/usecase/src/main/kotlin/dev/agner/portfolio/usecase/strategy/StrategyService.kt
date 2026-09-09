package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import org.springframework.stereotype.Service

@Service
class StrategyService(
    private val repository: IStrategyRepository,
) {

    suspend fun fetchAll() = repository.fetchAll()

    suspend fun create(creation: StrategyCreation) = repository.save(creation)
}
