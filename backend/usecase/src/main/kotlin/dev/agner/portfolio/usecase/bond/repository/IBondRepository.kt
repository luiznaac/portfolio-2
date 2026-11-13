package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.bond.model.BondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FloatingRateBondCreation

interface IBondRepository {

    suspend fun fetchAll(): Set<Bond>

    // The bond products a user manages: every bond not created by a checking-account deposit. An
    // account's internal bonds are that account's value, not products of their own.
    suspend fun fetchStandalone(): List<Bond>

    suspend fun fetchById(bondId: Int): Bond?

    suspend fun save(creation: BondCreation): Bond

    suspend fun save(checkingAccountId: Int, creation: FloatingRateBondCreation): FloatingRateBond

    suspend fun fetchBondsWithoutFullRedemptionOrMaturity(): List<Bond>

    suspend fun fetchByCheckingAccountId(checkingAccountId: Int): List<Bond>
}
