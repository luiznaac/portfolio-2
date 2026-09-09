package dev.agner.portfolio.persistence.attribution

import dev.agner.portfolio.persistence.listedasset.ListedAssetEntity
import dev.agner.portfolio.persistence.listedasset.ListedAssetTable
import dev.agner.portfolio.persistence.strategy.StrategyEntity
import dev.agner.portfolio.persistence.strategy.StrategyTable
import dev.agner.portfolio.usecase.attribution.model.AttributionMovement
import dev.agner.portfolio.usecase.attribution.model.AttributionReason
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object AttributionMovementTable : IntIdTable("attribution_movement") {
    val listedAsset = reference("listed_asset_id", ListedAssetTable.id)
    val strategy = reference("strategy_id", StrategyTable.id)
    val date = date("date")
    val quantity = decimal("quantity", 18, 8)
    val reason = varchar("reason", 20)
    val note = varchar("note", 255).nullable()
    val createdAt = datetime("created_at")
}

class AttributionMovementEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AttributionMovementEntity>(AttributionMovementTable)

    var listedAsset by ListedAssetEntity referencedOn AttributionMovementTable.listedAsset
    var strategy by StrategyEntity referencedOn AttributionMovementTable.strategy
    var date by AttributionMovementTable.date
    var quantity by AttributionMovementTable.quantity
    var reason by AttributionMovementTable.reason
    var note by AttributionMovementTable.note
    var createdAt by AttributionMovementTable.createdAt

    fun toModel() = AttributionMovement(
        id = id.value,
        listedAssetId = listedAsset.id.value,
        strategyId = strategy.id.value,
        date = date,
        quantity = quantity,
        reason = AttributionReason.valueOf(reason),
        note = note,
    )
}
