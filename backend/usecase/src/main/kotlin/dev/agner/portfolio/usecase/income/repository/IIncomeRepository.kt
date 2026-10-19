package dev.agner.portfolio.usecase.income.repository

import dev.agner.portfolio.usecase.income.model.Income
import dev.agner.portfolio.usecase.income.model.IncomeCreation

// The Income ledger port, implemented against Exposed in persistence. Reads select the INCOME
// tax-nature cash movements; `saveAll` persists the movements the service has already found novel.
interface IIncomeRepository {

    suspend fun fetchAll(): List<Income>

    suspend fun saveAll(creations: List<IncomeCreation>): List<Income>
}
