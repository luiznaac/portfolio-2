package dev.agner.portfolio.persistence.index

import dev.agner.portfolio.usecase.index.model.Index
import dev.agner.portfolio.usecase.index.model.IndexId
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object IndexTable : IdTable<String>("index") {
    override val id: Column<EntityID<String>> = varchar("id", 10).entityId()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

@OptIn(ExperimentalTime::class)
class IndexEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, IndexEntity>(IndexTable)

    var createdAt by IndexTable.createdAt

    fun toIndex() = Index(
        id = IndexId.valueOf(id.value),
    )
}
