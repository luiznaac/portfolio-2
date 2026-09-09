package dev.agner.portfolio.persistence.allocation

import org.jetbrains.exposed.v1.core.Table

// Plain table, composite PK (product_type, product_id): an override map, not user-owned data with
// its own identity — same rationale as TickerCatalogTable for skipping the DAO Entity ceremony.
object ProductClassificationTable : Table("product_classification") {
    val productType = varchar("product_type", 20)
    val productId = integer("product_id")
    val assetClass = varchar("asset_class", 20)

    override val primaryKey = PrimaryKey(productType, productId)
}
