package dev.agner.portfolio.persistence.strategy

import dev.agner.portfolio.persistence.configuration.ColumnSizes
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

object StrategyTargetTable : IntIdTable("strategy_target") {
    val strategyEdition = reference("strategy_edition_id", StrategyEditionTable.id)
    val ticker = varchar("ticker", ColumnSizes.TICKER_LENGTH)
    val weight = decimal("weight", ColumnSizes.WEIGHT_PRECISION, ColumnSizes.WEIGHT_SCALE)
    val rating = varchar("rating", ColumnSizes.RATING_LENGTH).nullable()
    val targetPrice = decimal("target_price", ColumnSizes.PRICE_PRECISION, ColumnSizes.PRICE_SCALE).nullable()

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
