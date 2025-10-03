package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import java.math.BigDecimal

interface IBondOrderRepository {

    suspend fun fetchAll(): Set<BondOrder>

    suspend fun fetchById(id: Int): BondOrder?

    suspend fun fetchByBondId(bondId: Int): List<BondOrder>

    suspend fun save(creation: BondOrderCreation): BondOrder

    suspend fun updateAmount(id: Int, amount: BigDecimal): BondOrder
}
