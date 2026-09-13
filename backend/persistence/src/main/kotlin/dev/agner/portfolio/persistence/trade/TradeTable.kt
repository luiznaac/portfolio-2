package dev.agner.portfolio.persistence.trade

import dev.agner.portfolio.persistence.configuration.ColumnSizes
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
    val quantity = decimal("quantity", ColumnSizes.QUANTITY_PRECISION, ColumnSizes.QUANTITY_SCALE)
    val price = decimal("price", ColumnSizes.PRICE_PRECISION, ColumnSizes.PRICE_SCALE)
    val createdAt = datetime("created_at")

    init { index(null, false, listedAsset, date) }
}

class TradeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TradeEntity>(TradeTable)

    var listedAsset by ListedAssetEntity referencedOn TradeTable.listedAsset
    var date by TradeTable.date
    var quantity by TradeTable.quantity
    var price by TradeTable.price
    var createdAt by TradeTable.createdAt

    // The column is one signed number; the domain is a Buy/Sell ADT. This is the only place that
    // translates between the two — see Trade.
    fun toModel(): Trade = if (quantity.signum() >= 0) {
        Trade.Buy(
            id = id.value,
            assetId = listedAsset.id.value,
            date = date,
            quantity = quantity,
            price = price,
        )
    } else {
        Trade.Sell(
            id = id.value,
            assetId = listedAsset.id.value,
            date = date,
            quantity = quantity.negate(),
            price = price,
        )
    }
}
