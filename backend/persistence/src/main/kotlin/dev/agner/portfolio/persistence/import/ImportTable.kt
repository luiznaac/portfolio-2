package dev.agner.portfolio.persistence.import

import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportDestination
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
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

    // Where an apply lands, recorded at staging so the parked session and the resume strip can
    // answer it from the record alone. The default keeps rows written before the column migrated.
    val destinationObject = enumerationByName<ImportDestinationObject>("destination_object", 20)
        .default(ImportDestinationObject.TRADES)
    val destinationEntityId = integer("destination_entity_id").nullable()

    // True when the uploaded bytes live in `import_file`; a typed-row staging has no original.
    val originalRetained = bool("original_retained").default(false)
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
    var destinationObject by ImportTable.destinationObject
    var destinationEntityId by ImportTable.destinationEntityId
    var originalRetained by ImportTable.originalRetained
    var createdAt by ImportTable.createdAt

    fun toModel() = Import(
        id = id.value,
        source = sourceType,
        fileName = fileName,
        fileHash = fileHash,
        status = status,
        destination = ImportDestination(destinationObject, destinationEntityId),
        hasOriginal = originalRetained,
        createdAt = createdAt,
    )
}
