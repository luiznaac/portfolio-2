package dev.agner.portfolio.persistence.import

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportCreation
import dev.agner.portfolio.usecase.import.model.ImportRow
import dev.agner.portfolio.usecase.import.model.ImportRowOutcome
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.import.repository.IImportRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ImportRepository(
    private val clock: Clock,
) : IImportRepository {

    override suspend fun fetchByHash(fileHash: String): Import? = transaction {
        ImportEntity.find { ImportTable.fileHash eq fileHash }.firstOrNull()?.toModel()
    }

    // The import and its rows (and the retained original) are written in one transaction so a
    // partial file can never land.
    override suspend fun save(creation: ImportCreation): Import = transaction {
        val import = ImportEntity.new {
            sourceType = creation.source
            fileName = creation.fileName
            fileHash = creation.fileHash
            status = ImportStatus.STAGED
            destinationObject = creation.destination.objectType
            destinationEntityId = creation.destination.entityId
            originalRetained = creation.original != null
            createdAt = LocalDateTime.now(clock)
        }

        creation.rows.forEach { row ->
            ImportRowEntity.new {
                owner = import
                date = row.date
                side = row.side
                venue = row.venue
                ticker = row.ticker
                quantity = row.quantity
                unitPrice = row.unitPrice
                institutionName = row.institutionName
                error = row.error
                createdAt = LocalDateTime.now(clock)
            }
        }

        creation.original?.let { bytes ->
            ImportFileEntity.new {
                owner = import
                content = bytes
            }
        }

        import.toModel()
    }

    // The optional filters are additive: no argument keeps the whole log, which is what the status
    // aggregate reads. The inbox and the import history narrow with them.
    override suspend fun fetchAll(status: ImportStatus?, source: ImportSource?): List<Import> = transaction {
        var condition: Op<Boolean> = Op.TRUE
        if (status != null) condition = condition and (ImportTable.status eq status)
        if (source != null) condition = condition and (ImportTable.sourceType eq source)

        ImportEntity.find { condition }
            .orderBy(ImportTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchById(id: Int): Import? = transaction {
        ImportEntity.findById(id)?.toModel()
    }

    override suspend fun fetchOriginal(id: Int): ByteArray? = transaction {
        ImportFileEntity.find { ImportFileTable.importId eq id }.firstOrNull()?.content
    }

    override suspend fun fetchRows(importId: Int): List<ImportRow> = transaction {
        ImportRowEntity.find { ImportRowTable.importId eq importId }
            .orderBy(ImportRowTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun updateStatus(id: Int, status: ImportStatus): Import? = transaction {
        ImportEntity.findById(id)?.apply { this.status = status }?.toModel()
    }

    override suspend fun saveOutcomes(importId: Int, outcomes: Map<Int, ImportRowOutcome>) = transaction {
        for (row in ImportRowEntity.find { ImportRowTable.importId eq importId }) {
            val outcome = outcomes[row.id.value] ?: continue
            row.outcome = outcome
        }
    }
}
