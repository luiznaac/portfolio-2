package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.usecase.bond.model.BondOrderYield
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object BondOrderYieldTable : IntIdTable("bond_oder_yield") {
    val bondOrderId = reference("bond_order_id", BondOrderTable.id)
    val date = date("date")
    val amount = decimal("amount", 12, 2)
    val createdAt = datetime("created_at")
}

class BondOrderYieldEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BondOrderYieldEntity>(BondOrderYieldTable)

    var bondOrderId by BondOrderEntity referencedOn BondOrderYieldTable.bondOrderId
    var date by BondOrderYieldTable.date
    var amount by BondOrderYieldTable.amount
    var createdAt by BondOrderYieldTable.createdAt

    fun toModel() = BondOrderYield(
        id = id.value,
        bondOrderId = bondOrderId.id.value,
        date = date,
        amount = amount.toDouble(),
    )
}
