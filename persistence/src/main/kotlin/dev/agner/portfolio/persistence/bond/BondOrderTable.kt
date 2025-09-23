package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object BondOrderTable : IntIdTable("bond_oder") {
    val bondId = reference("bond_id", BondTable.id)
    val type = varchar("type", 10)
    val date = date("date")
    val amount = decimal("amount", 12, 2)
    val createdAt = datetime("created_at")
}

class BondOrderEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BondOrderEntity>(BondOrderTable)

    var bond by BondEntity referencedOn BondOrderTable.bondId
    var type by BondOrderTable.type
    var date by BondOrderTable.date
    var amount by BondOrderTable.amount
    var createdAt by BondOrderTable.createdAt

    fun toModel() = BondOrder(
        id = id.value,
        bond = bond.toModel(),
        type = BondOrderType.valueOf(type),
        date = date,
        amount = amount.toDouble(),
    )
}
