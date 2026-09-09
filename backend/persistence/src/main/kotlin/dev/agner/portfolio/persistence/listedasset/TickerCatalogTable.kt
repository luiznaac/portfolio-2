package dev.agner.portfolio.persistence.listedasset

import org.jetbrains.exposed.v1.core.Table

// Plain (non-DAO) table: this is a read-mostly search index synced wholesale from brapi, upserted
// via batchInsert(ignore = true) rather than row-by-row entity mutation — no need for the DAO
// Entity/EntityClass ceremony the rest of the module uses for user-owned data.
object TickerCatalogTable : Table("ticker_catalog") {
    val ticker = varchar("ticker", 12)
    val name = varchar("name", 150).index()
    val kind = varchar("kind", 10)

    override val primaryKey = PrimaryKey(ticker)
}
