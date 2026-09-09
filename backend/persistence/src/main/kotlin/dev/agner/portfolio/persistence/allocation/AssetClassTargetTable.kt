package dev.agner.portfolio.persistence.allocation

import dev.agner.portfolio.usecase.allocation.model.AssetClass
import dev.agner.portfolio.usecase.allocation.model.AssetClassTarget
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object AssetClassTargetTable : IntIdTable("asset_class_target") {
    val assetClass = varchar("asset_class", 20)
    val weight = decimal("weight", 7, 4)
    val effectiveFrom = date("effective_from")
    val createdAt = datetime("created_at")

    init { index(null, false, assetClass, effectiveFrom) }
}

class AssetClassTargetEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AssetClassTargetEntity>(AssetClassTargetTable)

    var assetClass by AssetClassTargetTable.assetClass
    var weight by AssetClassTargetTable.weight
    var effectiveFrom by AssetClassTargetTable.effectiveFrom
    var createdAt by AssetClassTargetTable.createdAt

    fun toModel() = AssetClassTarget(
        id = id.value,
        assetClass = AssetClass.valueOf(assetClass),
        weight = weight,
        effectiveFrom = effectiveFrom,
    )
}
