package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import kotlin.reflect.KClass

interface IBondOrderRepository {

    suspend fun fetchByBondId(bondId: Int): List<BondOrder>

    suspend fun save(creation: BondOrderCreation): BondOrder

    suspend fun <T : BondOrder> updateType(id: Int, type: KClass<T>): BondOrder

    suspend fun fetchByCheckingAccountId(checkingAccountId: Int): List<BondOrder>
}
