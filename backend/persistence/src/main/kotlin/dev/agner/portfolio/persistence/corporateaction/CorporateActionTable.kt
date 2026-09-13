package dev.agner.portfolio.persistence.corporateaction

import dev.agner.portfolio.persistence.listedasset.ListedAssetEntity
import dev.agner.portfolio.persistence.listedasset.ListedAssetTable
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction.Bonus
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction.ReverseSplit
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction.Split
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction.TickerChange
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object CorporateActionTable : IntIdTable("corporate_action") {
    val listedAsset = reference("listed_asset_id", ListedAssetTable.id)
    val type = varchar("type", 20)
    val date = date("date")
    val ratio = decimal("ratio", 18, 8).nullable()
    val valuePerNewShare = decimal("value_per_new_share", 12, 4).nullable()
    val newTicker = varchar("new_ticker", 12).nullable()
    val createdAt = datetime("created_at")

    init { index(null, false, listedAsset, date) }
}

class CorporateActionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CorporateActionEntity>(CorporateActionTable)

    var listedAsset by ListedAssetEntity referencedOn CorporateActionTable.listedAsset
    var type by CorporateActionTable.type
    var date by CorporateActionTable.date
    var ratio by CorporateActionTable.ratio
    var valuePerNewShare by CorporateActionTable.valuePerNewShare
    var newTicker by CorporateActionTable.newTicker
    var createdAt by CorporateActionTable.createdAt

    fun toModel(): CorporateAction = when (type) {
        "SPLIT" -> Split(id = id.value, assetId = listedAsset.id.value, date = date, ratio = ratio!!)
        "REVERSE_SPLIT" -> ReverseSplit(id = id.value, assetId = listedAsset.id.value, date = date, ratio = ratio!!)
        "BONUS" -> Bonus(
            id = id.value,
            assetId = listedAsset.id.value,
            date = date,
            ratio = ratio!!,
            valuePerNewShare = valuePerNewShare!!,
        )
        "TICKER_CHANGE" -> TickerChange(
            id = id.value,
            assetId = listedAsset.id.value,
            date = date,
            newTicker = newTicker!!,
        )
        else -> error("Unknown corporate action type: $type")
    }
}
