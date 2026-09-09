package dev.agner.portfolio.persistence.strategy

import dev.agner.portfolio.usecase.strategy.model.StrategyWeight
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object StrategyWeightTable : IntIdTable("strategy_weight") {
    val strategy = reference("strategy_id", StrategyTable.id)
    val weight = decimal("weight", 7, 4)
    val effectiveFrom = date("effective_from")
    val createdAt = datetime("created_at")

    init { index(false, strategy, effectiveFrom) }
}

class StrategyWeightEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StrategyWeightEntity>(StrategyWeightTable)

    var strategy by StrategyEntity referencedOn StrategyWeightTable.strategy
    var weight by StrategyWeightTable.weight
    var effectiveFrom by StrategyWeightTable.effectiveFrom
    var createdAt by StrategyWeightTable.createdAt

    fun toModel() = StrategyWeight(
        id = id.value,
        strategyId = strategy.id.value,
        weight = weight,
        effectiveFrom = effectiveFrom,
    )
}
