package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.BondOrderStatement
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import kotlinx.datetime.LocalDate

interface IBondOrderStatementRepository {

    suspend fun fetchAll(): Set<BondOrderStatement>

    suspend fun fetchLastByBondOrderId(bondOrderId: Int): BondOrderStatement?

    suspend fun fetchAlreadyConsolidatedSellIdsByOrderId(bondId: Int): Set<Int>

    suspend fun fetchAlreadyRedeemedBuyIdsByOrderId(bondId: Int): Set<Int>

    suspend fun sumUpConsolidatedValues(buyOrderId: Int, date: LocalDate): Pair<Double, Double>

    suspend fun saveAll(creations: List<BondOrderStatementCreation>)
}
