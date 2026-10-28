package dev.agner.portfolio.usecase.listedasset.repository

import dev.agner.portfolio.usecase.listedasset.model.Attribution

interface IAttributionRepository {

    suspend fun fetchBy(ticker: String): List<Attribution>

    // Every stored slice — the funding pass needs the whole attribution ledger in one read.
    suspend fun fetchAll(): List<Attribution>

    // Upsert keyed on (ticker, strategyId) — the slice's quantity is its new total.
    suspend fun save(attribution: Attribution)

    suspend fun delete(ticker: String, strategyId: Int)
}
