package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.persistence.index.IndexEntity
import dev.agner.portfolio.persistence.index.IndexTable
import dev.agner.portfolio.usecase.bond.model.Bond.FixedRateBond
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.index.model.IndexId
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object BondTable : IntIdTable("bond") {
    val name = varchar("name", 100)
    val rateType = varchar("rate_type", 10)
    val value = decimal("value", 8, 4)
    val indexId = reference("index_id", IndexTable.id).nullable()
    val createdAt = datetime("created_at")
}

class BondEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BondEntity>(BondTable)

    var name by BondTable.name
    var rateType by BondTable.rateType
    var value by BondTable.value
    var indexId by IndexEntity optionalReferencedOn BondTable.indexId
    var createdAt by BondTable.createdAt

    fun toModel() = when (rateType) {
        "FIXED" -> FixedRateBond(id = id.value, name = name, value = value)
        "FLOATING" -> FloatingRateBond(
            id = id.value,
            name = name,
            value = value,
            indexId = IndexId.valueOf(indexId!!.id.value),
        )
        else -> throw IllegalStateException("Unknown rate type: $rateType")
    }
}
