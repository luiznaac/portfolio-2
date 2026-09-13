package dev.agner.portfolio.persistence.monthlyclose

import dev.agner.portfolio.persistence.configuration.ColumnSizes
import dev.agner.portfolio.usecase.monthlyclose.model.MonthlyClose
import dev.agner.portfolio.usecase.monthlyclose.model.MonthlyCloseStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object MonthlyCloseTable : IntIdTable("monthly_close") {
    val month = date("month").uniqueIndex()
    val status = enumerationByName("status", ColumnSizes.ENUM_NAME_LENGTH, MonthlyCloseStatus::class)
    val closedAt = datetime("closed_at").nullable()
    val createdAt = datetime("created_at")
}

class MonthlyCloseEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MonthlyCloseEntity>(MonthlyCloseTable)

    var month by MonthlyCloseTable.month
    var status by MonthlyCloseTable.status
    var closedAt by MonthlyCloseTable.closedAt
    var createdAt by MonthlyCloseTable.createdAt

    fun toModel() = MonthlyClose(
        id = id.value,
        month = month,
        status = status,
        closedAt = closedAt,
    )
}
