package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.BondOrderStatement
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import kotlinx.datetime.LocalDate

interface IBondOrderYieldRepository {

    suspend fun fetchAll(): Set<BondOrderStatement>

    suspend fun fetchLastByBondOrderId(bondOrderId: Int): BondOrderStatement?

    suspend fun sumYieldUntil(bondOrderId: Int, date: LocalDate): Double?

    suspend fun saveAll(creations: List<BondOrderStatementCreation>)
}
