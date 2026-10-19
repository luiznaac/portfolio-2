package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.persistence.strategy.StrategyEntity
import dev.agner.portfolio.persistence.strategy.StrategyTable
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

// The stored attribution slices. A ticker can be split across strategies, but each pair appears
// once: (ticker, strategy_id) is the natural key and the quantity is the slice's current total.
object AttributionTable : IntIdTable("attribution") {
    val ticker = varchar("ticker", 12)
    val strategyId = reference("strategy_id", StrategyTable.id)
    val quantity = decimal("quantity", 18, 6)

    init { uniqueIndex(ticker, strategyId) }
}

class AttributionEntity(id: EntityID<Int>) : IntEntity(id) {

    var ticker by AttributionTable.ticker
    var strategy by StrategyEntity referencedOn AttributionTable.strategyId
    var quantity by AttributionTable.quantity

    fun toModel() = Attribution(
        ticker = ticker,
        strategyId = strategy.id.value,
        quantity = quantity,
    )

    companion object : IntEntityClass<AttributionEntity>(AttributionTable)
}
