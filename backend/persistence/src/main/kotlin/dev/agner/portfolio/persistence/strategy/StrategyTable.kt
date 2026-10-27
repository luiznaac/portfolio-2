package dev.agner.portfolio.persistence.strategy

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object StrategyTable : IntIdTable("strategy") {
    // The report names the strategy, so the name is its natural key: creating the same strategy
    // twice is a mistake, not a second bucket.
    val name = varchar("name", 100)
    val assetClass = enumerationByName<AssetClass>("asset_class", 20)
    val kind = enumerationByName<StrategyKind>("kind", 20)
    val createdAt = datetime("created_at")

    init { uniqueIndex(name) }
}

@OptIn(ExperimentalTime::class)
class StrategyEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StrategyEntity>(StrategyTable)

    var name by StrategyTable.name
    var assetClass by StrategyTable.assetClass
    var kind by StrategyTable.kind
    var createdAt by StrategyTable.createdAt

    fun toModel() = Strategy(
        id = id.value,
        name = name,
        assetClass = assetClass,
        kind = kind,
    )
}
