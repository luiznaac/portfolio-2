package dev.agner.portfolio.usecase.income.repository

import dev.agner.portfolio.usecase.income.model.Income
import dev.agner.portfolio.usecase.income.model.IncomeCreation

// The Income ledger port, implemented against Exposed in persistence. Reads select the INCOME
// tax-nature cash movements; `saveAll` persists the movements the service has already found novel.
interface IIncomeRepository {

    suspend fun fetchAll(): List<Income>

    suspend fun saveAll(creations: List<IncomeCreation>): List<Income>

    // Marks the matched movements RECONCILED — the one lifecycle stage the stored row owns, set by
    // the Movimentações match. The amounts are not rewritten: the announced export is the forecast,
    // the movement is the confirmation.
    suspend fun markReconciled(ids: List<Int>)
}
