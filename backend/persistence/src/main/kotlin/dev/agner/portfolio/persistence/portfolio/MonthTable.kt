package dev.agner.portfolio.persistence.portfolio

import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import kotlinx.datetime.YearMonth
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object MonthTable : IntIdTable("month") {
    // Stored as the ISO "YYYY-MM" string the edge speaks; the period is the natural unique key,
    // since opening the same month twice is a mistake, not a second month.
    val period = varchar("period", 7)
    val capital = decimal("capital", 18, 6)
    val floor = decimal("floor", 18, 6)
    val status = enumerationByName<MonthStatus>("status", 20)
    val createdAt = datetime("created_at")

    init { uniqueIndex(period) }
}

@OptIn(ExperimentalTime::class)
class MonthEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MonthEntity>(MonthTable)

    var period by MonthTable.period
    var capital by MonthTable.capital
    var floor by MonthTable.floor
    var status by MonthTable.status
    var createdAt by MonthTable.createdAt

    fun toModel() = Month(
        id = id.value,
        period = YearMonth.parse(period),
        capital = capital,
        floor = floor,
        status = status,
    )
}
