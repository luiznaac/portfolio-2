package dev.agner.portfolio.persistence.strategy

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

object StrategyTargetTable : IntIdTable("strategy_target") {
    val strategyEdition = reference("strategy_edition_id", StrategyEditionTable.id)
    val ticker = varchar("ticker", 12)
    val weight = decimal("weight", 7, 4)
    val rating = varchar("rating", 20).nullable()
    val targetPrice = decimal("target_price", 12, 4).nullable()

    init { index(false, strategyEdition) }
}

class StrategyTargetEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StrategyTargetEntity>(StrategyTargetTable)

    var strategyEdition by StrategyEditionEntity referencedOn StrategyTargetTable.strategyEdition
    var ticker by StrategyTargetTable.ticker
    var weight by StrategyTargetTable.weight
    var rating by StrategyTargetTable.rating
    var targetPrice by StrategyTargetTable.targetPrice
}
