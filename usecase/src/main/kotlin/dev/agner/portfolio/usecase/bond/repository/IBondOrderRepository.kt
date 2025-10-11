package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType

interface IBondOrderRepository {

    suspend fun fetchAll(): Set<BondOrder>

    suspend fun fetchById(id: Int): BondOrder?

    suspend fun fetchByBondId(bondId: Int): List<BondOrder>

    suspend fun save(creation: BondOrderCreation): BondOrder

    suspend fun updateType(id: Int, type: BondOrderType): BondOrder
}
