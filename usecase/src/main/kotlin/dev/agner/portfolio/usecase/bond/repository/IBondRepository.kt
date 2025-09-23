package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.BondCreation

interface IBondRepository {

    suspend fun fetchAllBonds(): Set<Bond>

    suspend fun save(creation: BondCreation): Bond
}
