package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyWeightCreation
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import dev.agner.portfolio.usecase.strategy.repository.IStrategyWeightRepository
import org.springframework.stereotype.Service

@Service
class StrategyService(
    private val repository: IStrategyRepository,
    private val weightRepository: IStrategyWeightRepository,
) {

    suspend fun fetchAll() = repository.fetchAll()

    suspend fun create(creation: StrategyCreation) = repository.save(creation)

    suspend fun fetchWeightHistory() = weightRepository.fetchAll()

    suspend fun setWeight(strategyId: Int, creation: StrategyWeightCreation) =
        weightRepository.save(strategyId, creation)
}
