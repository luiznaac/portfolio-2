package dev.agner.portfolio.persistence.catalog

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object CatalogTable : IdTable<String>("ticker_catalog") {
    override val id = varchar("ticker", 12).entityId()
    val shortName = varchar("short_name", 12)
    val bdiType = varchar("bdi_type", 2)
    val isin = varchar("isin", 12).nullable()
    val assetKind = enumerationByName<AssetKind>("asset_kind", 20)
    val assetClass = enumerationByName<AssetClass>("asset_class", 20).nullable()
    val firstSeen = date("first_seen")
    val lastSeen = date("last_seen")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

@OptIn(ExperimentalTime::class)
class CatalogEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, CatalogEntity>(CatalogTable)

    var shortName by CatalogTable.shortName
    var bdiType by CatalogTable.bdiType
    var isin by CatalogTable.isin
    var assetKind by CatalogTable.assetKind
    var assetClass by CatalogTable.assetClass
    var firstSeen by CatalogTable.firstSeen
    var lastSeen by CatalogTable.lastSeen
    var createdAt by CatalogTable.createdAt

    fun toModel() = CatalogEntry(
        ticker = id.value,
        shortName = shortName,
        bdiType = bdiType,
        isin = isin,
        assetKind = assetKind,
        assetClass = assetClass,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
    )
}
