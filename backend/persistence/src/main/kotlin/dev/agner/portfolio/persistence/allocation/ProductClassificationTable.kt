package dev.agner.portfolio.persistence.allocation

import dev.agner.portfolio.persistence.configuration.ColumnSizes
import org.jetbrains.exposed.v1.core.Table

// Plain table, composite PK (product_type, product_id): an override map, not user-owned data with
// its own identity — same rationale as TickerCatalogTable for skipping the DAO Entity ceremony.
object ProductClassificationTable : Table("product_classification") {
    val productType = varchar("product_type", ColumnSizes.ENUM_NAME_LENGTH)
    val productId = integer("product_id")
    val assetClass = varchar("asset_class", ColumnSizes.ENUM_NAME_LENGTH)

    override val primaryKey = PrimaryKey(productType, productId)
}
