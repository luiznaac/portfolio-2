package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.BondCreation

interface IBondRepository {

    suspend fun fetchAll(): Set<Bond>

    suspend fun fetchById(bondId: Int): Bond?

    suspend fun save(creation: BondCreation): Bond
}
