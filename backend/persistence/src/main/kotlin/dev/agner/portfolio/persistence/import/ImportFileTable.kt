package dev.agner.portfolio.persistence.import

import org.jetbrains.exposed.v1.core.BasicBinaryColumnType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import kotlin.time.ExperimentalTime

// MySQL's BLOB caps at 64 KiB, which is smaller than a real broker workbook; the retained original
// needs LONGBLOB. Exposed's binary column asks the dialect for BLOB, so this is the one override.
private object LongBlobColumnType : BasicBinaryColumnType() {
    override fun sqlType(): String = "LONGBLOB"
}

// The uploaded bytes kept beside the import for provenance. A separate table keeps the import list
// read cheap — the list never has to load a blob to answer whether an original exists.
@OptIn(ExperimentalTime::class)
object ImportFileTable : IntIdTable("import_file") {
    val importId = reference("import_id", ImportTable.id)
    val content = registerColumn("content", LongBlobColumnType)

    init { uniqueIndex(importId) }
}

class ImportFileEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ImportFileEntity>(ImportFileTable)

    // `import` is a Kotlin keyword, so the owning relation is named `owner`.
    var owner by ImportEntity referencedOn ImportFileTable.importId
    var content by ImportFileTable.content
}
