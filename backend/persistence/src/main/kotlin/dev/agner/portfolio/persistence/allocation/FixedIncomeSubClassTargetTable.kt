package dev.agner.portfolio.persistence.allocation

import dev.agner.portfolio.persistence.configuration.ColumnSizes
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClassTarget
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object FixedIncomeSubClassTargetTable : IntIdTable("fixed_income_subclass_target") {
    val subClass = varchar("sub_class", ColumnSizes.ENUM_NAME_LENGTH)
    val weight = decimal("weight", ColumnSizes.WEIGHT_PRECISION, ColumnSizes.WEIGHT_SCALE)
    val effectiveFrom = date("effective_from")
    val createdAt = datetime("created_at")

    init { index(null, false, subClass, effectiveFrom) }
}

class FixedIncomeSubClassTargetEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<FixedIncomeSubClassTargetEntity>(FixedIncomeSubClassTargetTable)

    var subClass by FixedIncomeSubClassTargetTable.subClass
    var weight by FixedIncomeSubClassTargetTable.weight
    var effectiveFrom by FixedIncomeSubClassTargetTable.effectiveFrom
    var createdAt by FixedIncomeSubClassTargetTable.createdAt

    fun toModel() = FixedIncomeSubClassTarget(
        id = id.value,
        subClass = FixedIncomeSubClass.valueOf(subClass),
        weight = weight,
        effectiveFrom = effectiveFrom,
    )
}
