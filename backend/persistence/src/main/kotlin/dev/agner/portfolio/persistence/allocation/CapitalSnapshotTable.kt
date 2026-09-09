package dev.agner.portfolio.persistence.allocation

import dev.agner.portfolio.usecase.allocation.model.CapitalSnapshot
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object CapitalSnapshotTable : IntIdTable("capital_snapshot") {
    val date = date("date")
    val externalBalance = decimal("external_balance", 14, 2)
    val plannedContribution = decimal("planned_contribution", 14, 2)
    val createdAt = datetime("created_at")
}

class CapitalSnapshotEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CapitalSnapshotEntity>(CapitalSnapshotTable)

    var date by CapitalSnapshotTable.date
    var externalBalance by CapitalSnapshotTable.externalBalance
    var plannedContribution by CapitalSnapshotTable.plannedContribution
    var createdAt by CapitalSnapshotTable.createdAt

    fun toModel() = CapitalSnapshot(
        id = id.value,
        date = date,
        externalBalance = externalBalance,
        plannedContribution = plannedContribution,
    )
}
