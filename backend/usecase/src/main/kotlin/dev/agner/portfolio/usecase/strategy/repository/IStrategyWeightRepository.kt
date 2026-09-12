package dev.agner.portfolio.usecase.strategy.repository

import dev.agner.portfolio.usecase.strategy.model.StrategyWeight
import dev.agner.portfolio.usecase.strategy.model.StrategyWeightCreation
import kotlinx.datetime.LocalDate

interface IStrategyWeightRepository {

    suspend fun fetchAll(): List<StrategyWeight>

    /** The latest weight per strategyId with effectiveFrom on or before [date]. */
    suspend fun fetchCurrent(date: LocalDate): List<StrategyWeight>

    suspend fun save(strategyId: Int, creation: StrategyWeightCreation): StrategyWeight
}
