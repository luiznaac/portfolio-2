package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.persistence.configuration.ColumnSizes
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object ListedAssetPositionTable : IntIdTable("listed_asset_position") {
    val listedAsset = reference("listed_asset_id", ListedAssetTable.id)
    val date = date("date")
    val principal = decimal("principal", ColumnSizes.BALANCE_PRECISION, ColumnSizes.BALANCE_SCALE)
    val yieldValue = decimal("yield", ColumnSizes.BALANCE_PRECISION, ColumnSizes.BALANCE_SCALE)
    val taxes = decimal("taxes", ColumnSizes.BALANCE_PRECISION, ColumnSizes.BALANCE_SCALE)
    val createdAt = datetime("created_at")

    init { uniqueIndex(listedAsset, date) }
}

class ListedAssetPositionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ListedAssetPositionEntity>(ListedAssetPositionTable)

    var listedAsset by ListedAssetEntity referencedOn ListedAssetPositionTable.listedAsset
    var date by ListedAssetPositionTable.date
    var principal by ListedAssetPositionTable.principal
    var yieldValue by ListedAssetPositionTable.yieldValue
    var taxes by ListedAssetPositionTable.taxes
    var createdAt by ListedAssetPositionTable.createdAt
}
