package dev.agner.portfolio.persistence.index

import dev.agner.portfolio.usecase.index.model.IndexValue
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object IndexValueTable : IntIdTable("index_value") {
    val indexId = reference("index_id", IndexTable.id)
    val date = date("date")
    val value = decimal("value", 12, 8)
    val createdAt = datetime("created_at")
}

class IndexValueEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<IndexValueEntity>(IndexValueTable)

    var indexId by IndexEntity referencedOn IndexValueTable.indexId
    var date by IndexValueTable.date
    var value by IndexValueTable.value
    var createdAt by IndexValueTable.createdAt

    fun toIndexValue() = IndexValue(
        date = date,
        value = value.toDouble(),
    )
}
