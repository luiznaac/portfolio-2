package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.usecase.listedasset.catalog.model.TickerCatalogEntry
import dev.agner.portfolio.usecase.listedasset.catalog.repository.ITickerCatalogRepository
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component

@Component
class TickerCatalogRepository : ITickerCatalogRepository {

    override suspend fun count(): Long = transaction { TickerCatalogTable.selectAll().count() }

    override suspend fun search(query: String, limit: Int): List<TickerCatalogEntry> = transaction {
        val pattern = "%$query%"
        TickerCatalogTable
            .selectAll()
            .where { (TickerCatalogTable.ticker like pattern) or (TickerCatalogTable.name like pattern) }
            .orderBy(TickerCatalogTable.ticker to SortOrder.ASC)
            .limit(limit)
            .map { it.toModel() }
    }

    override suspend fun upsertAll(entries: List<TickerCatalogEntry>): Unit = transaction {
        TickerCatalogTable.batchInsert(entries, ignore = true) { entry ->
            this[TickerCatalogTable.ticker] = entry.ticker
            this[TickerCatalogTable.name] = entry.name
            this[TickerCatalogTable.kind] = entry.kind.name
        }
    }
}

private fun ResultRow.toModel() = TickerCatalogEntry(
    ticker = this[TickerCatalogTable.ticker],
    name = this[TickerCatalogTable.name],
    kind = AssetKind.valueOf(this[TickerCatalogTable.kind]),
)
