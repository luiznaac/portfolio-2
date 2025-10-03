package dev.agner.portfolio.usecase.bond.repository

import dev.agner.portfolio.usecase.bond.model.BondOrderStatement
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

interface IBondOrderStatementRepository {

    suspend fun fetchAllByBondId(bondId: Int): List<BondOrderStatement>

    suspend fun fetchLastByBondOrderId(bondOrderId: Int): BondOrderStatement?

    suspend fun fetchAlreadyConsolidatedSellIdsByOrderId(bondId: Int): Set<Int>

    suspend fun fetchAlreadyRedeemedBuyIdsByOrderId(bondId: Int): Set<Int>

    suspend fun sumUpConsolidatedValues(buyOrderId: Int, date: LocalDate): Pair<BigDecimal, BigDecimal>

    suspend fun saveAll(creations: List<BondOrderStatementCreation>)
}
