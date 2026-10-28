package dev.agner.portfolio.persistence.institution

import dev.agner.portfolio.usecase.institution.model.Institution
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object InstitutionTable : IntIdTable("institution") {
    val name = varchar("name", 100)
    val createdAt = datetime("created_at")

    // The stored name is already normalized, so it is the natural unique key (merge-on-collision).
    init { uniqueIndex(name) }
}

@OptIn(ExperimentalTime::class)
class InstitutionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<InstitutionEntity>(InstitutionTable)

    var name by InstitutionTable.name
    var createdAt by InstitutionTable.createdAt

    fun toModel() = Institution(
        id = id.value,
        name = name,
    )
}
