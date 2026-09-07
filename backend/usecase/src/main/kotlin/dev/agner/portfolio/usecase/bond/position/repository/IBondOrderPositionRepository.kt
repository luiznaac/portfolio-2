package dev.agner.portfolio.usecase.bond.position.repository

import dev.agner.portfolio.usecase.bond.position.model.BondOrderPosition

interface IBondOrderPositionRepository {

    suspend fun saveAll(positions: List<BondOrderPosition>)

    suspend fun fetchByBondId(bondId: Int): List<BondOrderPosition>

    suspend fun fetchLastByBondOrderId(bondOrderId: List<Int>): List<BondOrderPosition>
}
