package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.usecase.bond.model.BondOrderStatement
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object BondOrderStatementTable : IntIdTable("bond_oder_statement") {
    val bondOrderId = reference("bond_order_id", BondOrderTable.id)
    val type = varchar("type", 20)
    val date = date("date")
    val amount = decimal("amount", 12, 2)
    val createdAt = datetime("created_at")
}

class BondOrderStatementEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BondOrderStatementEntity>(BondOrderStatementTable)

    var bondOrderId by BondOrderEntity referencedOn BondOrderStatementTable.bondOrderId
    var type by BondOrderStatementTable.type
    var date by BondOrderStatementTable.date
    var amount by BondOrderStatementTable.amount
    var createdAt by BondOrderStatementTable.createdAt

    fun toModel() = BondOrderStatement(
        id = id.value,
        bondOrderId = bondOrderId.id.value,
        date = date,
        amount = amount.toDouble(),
    )
}
