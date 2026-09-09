package dev.agner.portfolio.usecase.allocation.repository

import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClassTarget
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClassTargetCreation
import kotlinx.datetime.LocalDate

interface IFixedIncomeSubClassTargetRepository {

    suspend fun fetchAll(): List<FixedIncomeSubClassTarget>

    /** The latest target per FixedIncomeSubClass with effectiveFrom on or before [date]. */
    suspend fun fetchCurrent(date: LocalDate): List<FixedIncomeSubClassTarget>

    suspend fun save(creation: FixedIncomeSubClassTargetCreation): FixedIncomeSubClassTarget
}
