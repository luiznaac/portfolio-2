package dev.agner.portfolio.usecase.strategy.repository

import dev.agner.portfolio.usecase.strategy.model.StrategyEdition
import dev.agner.portfolio.usecase.strategy.model.StrategyEditionCreation
import kotlinx.datetime.LocalDate

interface IStrategyEditionRepository {

    /** Oldest first — callers that need the diff chain rely on this order. */
    suspend fun fetchByStrategyId(strategyId: Int): List<StrategyEdition>

    suspend fun exists(strategyId: Int, referenceDate: LocalDate): Boolean

    suspend fun save(creation: StrategyEditionCreation): StrategyEdition
}
