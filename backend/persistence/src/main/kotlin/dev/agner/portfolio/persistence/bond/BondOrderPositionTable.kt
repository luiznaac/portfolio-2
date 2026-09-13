package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.persistence.configuration.ColumnSizes
import dev.agner.portfolio.usecase.bond.position.model.BondOrderPosition
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object BondOrderPositionTable : IntIdTable("bond_order_position") {
    val bondOrderId = reference("bond_order_id", BondOrderTable.id)
    val date = date("date")
    val principal = decimal("principal", ColumnSizes.BALANCE_PRECISION, ColumnSizes.BALANCE_SCALE)
    val yieldValue = decimal("yield", ColumnSizes.BALANCE_PRECISION, ColumnSizes.BALANCE_SCALE)
    val taxes = decimal("taxes", ColumnSizes.BALANCE_PRECISION, ColumnSizes.BALANCE_SCALE)
    val createdAt = datetime("created_at")
}

class BondOrderPositionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BondOrderPositionEntity>(BondOrderPositionTable)

    var bondOrder by BondOrderEntity referencedOn BondOrderPositionTable.bondOrderId
    var date by BondOrderPositionTable.date
    var principal by BondOrderPositionTable.principal
    var yieldValue by BondOrderPositionTable.yieldValue
    var taxes by BondOrderPositionTable.taxes
    var createdAt by BondOrderPositionTable.createdAt

    fun toModel(): BondOrderPosition = BondOrderPosition(
        bondOrderId = bondOrder.id.value,
        date = date,
        principal = principal,
        yield = yieldValue,
        taxes = taxes,
    )
}
