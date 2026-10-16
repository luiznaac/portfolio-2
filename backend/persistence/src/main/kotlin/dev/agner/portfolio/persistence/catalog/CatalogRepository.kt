package dev.agner.portfolio.persistence.catalog

import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class CatalogRepository(
    private val clock: Clock,
) : ICatalogRepository {

    // Upsert, never delete: on conflict every descriptive column and last_seen move forward,
    // while first_seen and created_at stay pinned to the ticker's first sighting. The conflict
    // keys are left implicit — MySQL's ON DUPLICATE KEY UPDATE uses the table's unique keys (here
    // the ticker primary key) and rejects an explicit conflict-column list.
    override suspend fun upsertAll(entries: List<CatalogEntry>): Int = transaction {
        if (entries.isEmpty()) return@transaction 0

        CatalogTable.batchUpsert(
            entries,
            onUpdateExclude = listOf(CatalogTable.firstSeen, CatalogTable.createdAt),
        ) { entry ->
            this[CatalogTable.id] = entry.ticker
            this[CatalogTable.shortName] = entry.shortName
            this[CatalogTable.bdiType] = entry.bdiType
            this[CatalogTable.isin] = entry.isin
            this[CatalogTable.assetKind] = entry.assetKind
            this[CatalogTable.assetClass] = entry.assetClass
            this[CatalogTable.firstSeen] = entry.firstSeen
            this[CatalogTable.lastSeen] = entry.lastSeen
            this[CatalogTable.createdAt] = LocalDateTime.now(clock)
        }

        entries.size
    }

    override suspend fun fetchAll(): List<CatalogEntry> = transaction {
        CatalogEntity.all()
            .orderBy(CatalogTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchBy(ticker: String): CatalogEntry? = transaction {
        CatalogEntity.findById(ticker)?.toModel()
    }
}
