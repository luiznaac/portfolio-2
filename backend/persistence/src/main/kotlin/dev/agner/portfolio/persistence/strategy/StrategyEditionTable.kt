package dev.agner.portfolio.persistence.strategy

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object StrategyEditionTable : IntIdTable("strategy_edition") {
    val strategy = reference("strategy_id", StrategyTable.id)
    val referenceDate = date("reference_date")
    val changesText = text("changes_text").nullable()
    val createdAt = datetime("created_at")

    init { uniqueIndex(strategy, referenceDate) }
}

class StrategyEditionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StrategyEditionEntity>(StrategyEditionTable)

    var strategy by StrategyEntity referencedOn StrategyEditionTable.strategy
    var referenceDate by StrategyEditionTable.referenceDate
    var changesText by StrategyEditionTable.changesText
    var createdAt by StrategyEditionTable.createdAt
}
