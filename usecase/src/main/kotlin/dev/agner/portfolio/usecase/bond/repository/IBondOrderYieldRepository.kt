package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.BondOrderYield
import dev.agner.portfolio.usecase.bond.model.BondOrderYieldCreation

interface IBondOrderYieldRepository {

    suspend fun fetchAll(): Set<BondOrderYield>

    suspend fun saveAll(bondOrderId: Int, creations: List<BondOrderYieldCreation>)
}
