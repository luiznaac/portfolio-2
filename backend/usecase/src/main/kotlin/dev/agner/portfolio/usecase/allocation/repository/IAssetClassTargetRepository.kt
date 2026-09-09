package dev.agner.portfolio.usecase.allocation.repository

import dev.agner.portfolio.usecase.allocation.model.AssetClassTarget
import dev.agner.portfolio.usecase.allocation.model.AssetClassTargetCreation
import kotlinx.datetime.LocalDate

interface IAssetClassTargetRepository {

    suspend fun fetchAll(): List<AssetClassTarget>

    /** The latest target per AssetClass with effectiveFrom on or before [date]. */
    suspend fun fetchCurrent(date: LocalDate): List<AssetClassTarget>

    suspend fun save(creation: AssetClassTargetCreation): AssetClassTarget
}
