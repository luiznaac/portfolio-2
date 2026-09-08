package dev.agner.portfolio.persistence.listedasset

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object ListedAssetTickerHistoryTable : IntIdTable("listed_asset_ticker_history") {
    val listedAsset = reference("listed_asset_id", ListedAssetTable.id)
    val ticker = varchar("ticker", 12)
    val effectiveFrom = date("effective_from")
    val effectiveTo = date("effective_to").nullable()
    val createdAt = datetime("created_at")
}

class ListedAssetTickerHistoryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ListedAssetTickerHistoryEntity>(ListedAssetTickerHistoryTable)

    var listedAsset by ListedAssetEntity referencedOn ListedAssetTickerHistoryTable.listedAsset
    var ticker by ListedAssetTickerHistoryTable.ticker
    var effectiveFrom by ListedAssetTickerHistoryTable.effectiveFrom
    var effectiveTo by ListedAssetTickerHistoryTable.effectiveTo
    var createdAt by ListedAssetTickerHistoryTable.createdAt
}
