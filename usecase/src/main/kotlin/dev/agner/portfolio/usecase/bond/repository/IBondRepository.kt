package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.bond.model.BondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FloatingRateBondCreation

interface IBondRepository {

    suspend fun fetchAll(): Set<Bond>

    suspend fun fetchById(bondId: Int): Bond?

    suspend fun save(creation: BondCreation): Bond

    suspend fun save(checkingAccountId: Int, creation: FloatingRateBondCreation): FloatingRateBond
}
