package dev.agner.portfolio.persistence.strategy

import dev.agner.portfolio.usecase.allocation.model.AssetClass
import dev.agner.portfolio.usecase.strategy.model.Strategy
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime

object StrategyTable : IntIdTable("strategy") {
    val name = varchar("name", 100)
    val assetClass = varchar("asset_class", 20)
    val createdAt = datetime("created_at")
}

class StrategyEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StrategyEntity>(StrategyTable)

    var name by StrategyTable.name
    var assetClass by StrategyTable.assetClass
    var createdAt by StrategyTable.createdAt

    fun toModel() = Strategy(id = id.value, name = name, assetClass = AssetClass.valueOf(assetClass))
}
