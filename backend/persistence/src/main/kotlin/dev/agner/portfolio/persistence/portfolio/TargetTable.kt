package dev.agner.portfolio.persistence.portfolio

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.portfolio.model.TargetLevel
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object TargetTable : IntIdTable("target") {
    val monthId = reference("month_id", MonthTable.id)
    val level = enumerationByName<TargetLevel>("level", 20)

    // One row per chain rung, so exactly the key columns its level needs are set and the rest stay
    // null. The repository is the one place that reads and writes this discriminated shape — the
    // domain sees the grouped Targets instead.
    val assetClass = enumerationByName<AssetClass>("asset_class", 20).nullable()
    val strategy = varchar("strategy", 100).nullable()
    val subClass = enumerationByName<FixedIncomeSubClass>("sub_class", 20).nullable()
    val ticker = varchar("ticker", 12).nullable()
    val percentage = decimal("percentage", 18, 6)
    val createdAt = datetime("created_at")
}

@OptIn(ExperimentalTime::class)
class TargetEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TargetEntity>(TargetTable)

    var month by MonthEntity referencedOn TargetTable.monthId
    var level by TargetTable.level
    var assetClass by TargetTable.assetClass
    var strategy by TargetTable.strategy
    var subClass by TargetTable.subClass
    var ticker by TargetTable.ticker
    var percentage by TargetTable.percentage
    var createdAt by TargetTable.createdAt
}
