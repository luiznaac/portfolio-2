package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation

interface IBondOrderRepository {

    suspend fun fetchAll(): Set<BondOrder>

    suspend fun fetchByBondId(bondId: Int): List<BondOrder>

    suspend fun save(creation: BondOrderCreation): BondOrder
}
