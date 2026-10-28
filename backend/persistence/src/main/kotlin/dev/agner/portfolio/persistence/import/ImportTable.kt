package dev.agner.portfolio.persistence.import

import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object ImportTable : IntIdTable("import") {
    // `source` is taken by ColumnSet, so the property differs from the column name.
    val sourceType = enumerationByName<ImportSource>("source", 20)
    val fileName = varchar("file_name", 255)

    // The file's identity: a unique index is what makes a re-import a no-op at the database level.
    val fileHash = varchar("file_hash", 64)
    val status = enumerationByName<ImportStatus>("status", 20)
    val createdAt = datetime("created_at")

    init { uniqueIndex(fileHash) }
}

@OptIn(ExperimentalTime::class)
class ImportEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ImportEntity>(ImportTable)

    var sourceType by ImportTable.sourceType
    var fileName by ImportTable.fileName
    var fileHash by ImportTable.fileHash
    var status by ImportTable.status
    var createdAt by ImportTable.createdAt

    fun toModel() = Import(
        id = id.value,
        source = sourceType,
        fileName = fileName,
        fileHash = fileHash,
        status = status,
        createdAt = createdAt,
    )
}
