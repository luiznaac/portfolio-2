package dev.agner.portfolio.persistence.trade

import dev.agner.portfolio.persistence.listedasset.ListedAssetEntity
import dev.agner.portfolio.persistence.listedasset.ListedAssetTable
import dev.agner.portfolio.usecase.trade.model.Trade
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object TradeTable : IntIdTable("trade") {
    val listedAsset = reference("listed_asset_id", ListedAssetTable.id)
    val date = date("date")
    val quantity = decimal("quantity", 18, 8)
    val price = decimal("price", 12, 4)
    val createdAt = datetime("created_at")
}

class TradeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TradeEntity>(TradeTable)

    var listedAsset by ListedAssetEntity referencedOn TradeTable.listedAsset
    var date by TradeTable.date
    var quantity by TradeTable.quantity
    var price by TradeTable.price
    var createdAt by TradeTable.createdAt

    fun toModel() = Trade(
        id = id.value,
        assetId = listedAsset.id.value,
        date = date,
        quantity = quantity,
        price = price,
    )
}
