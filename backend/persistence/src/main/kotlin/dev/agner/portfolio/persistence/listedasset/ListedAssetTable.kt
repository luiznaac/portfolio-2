package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime

object ListedAssetTable : IntIdTable("listed_asset") {
    val ticker = varchar("ticker", 12).uniqueIndex()
    val kind = varchar("kind", 10)
    val name = varchar("name", 150)
    val b3Identifier = varchar("b3_identifier", 100)
    val createdAt = datetime("created_at")
}

class ListedAssetEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ListedAssetEntity>(ListedAssetTable)

    var ticker by ListedAssetTable.ticker
    var kind by ListedAssetTable.kind
    var name by ListedAssetTable.name
    var b3Identifier by ListedAssetTable.b3Identifier
    var createdAt by ListedAssetTable.createdAt

    fun toModel() = ListedAsset(
        id = id.value,
        ticker = ticker,
        kind = AssetKind.valueOf(kind),
        name = name,
        b3Identifier = b3Identifier,
    )
}
