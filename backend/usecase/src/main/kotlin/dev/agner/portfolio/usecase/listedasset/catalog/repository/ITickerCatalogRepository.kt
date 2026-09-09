package dev.agner.portfolio.usecase.listedasset.catalog.repository

import dev.agner.portfolio.usecase.listedasset.catalog.model.TickerCatalogEntry

interface ITickerCatalogRepository {
    suspend fun count(): Long
    suspend fun search(query: String, limit: Int): List<TickerCatalogEntry>
    suspend fun upsertAll(entries: List<TickerCatalogEntry>)
}
