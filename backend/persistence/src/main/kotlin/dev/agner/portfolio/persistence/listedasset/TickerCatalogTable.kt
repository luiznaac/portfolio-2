package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.persistence.configuration.ColumnSizes
import org.jetbrains.exposed.v1.core.Table

// Plain (non-DAO) table: this is a read-mostly search index synced wholesale from brapi, upserted
// via batchInsert(ignore = true) rather than row-by-row entity mutation — no need for the DAO
// Entity/EntityClass ceremony the rest of the module uses for user-owned data.
object TickerCatalogTable : Table("ticker_catalog") {
    val ticker = varchar("ticker", ColumnSizes.TICKER_LENGTH)
    val name = varchar("name", ColumnSizes.LISTED_ASSET_NAME_LENGTH).index()
    val kind = varchar("kind", ColumnSizes.ASSET_KIND_LENGTH)

    override val primaryKey = PrimaryKey(ticker)
}
