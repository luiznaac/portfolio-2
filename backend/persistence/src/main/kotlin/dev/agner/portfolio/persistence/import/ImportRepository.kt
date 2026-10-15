package dev.agner.portfolio.persistence.import

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportCreation
import dev.agner.portfolio.usecase.import.model.ImportRow
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.import.repository.IImportRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
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

    // The import and its rows are written in one transaction so a partial file can never land.
    override suspend fun save(creation: ImportCreation): Import = transaction {
        val import = ImportEntity.new {
            sourceType = creation.source
            fileName = creation.fileName
            fileHash = creation.fileHash
            status = ImportStatus.STAGED
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

        import.toModel()
    }

    override suspend fun fetchAll(): List<Import> = transaction {
        ImportEntity.all()
            .orderBy(ImportTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchById(id: Int): Import? = transaction {
        ImportEntity.findById(id)?.toModel()
    }

    override suspend fun fetchRows(importId: Int): List<ImportRow> = transaction {
        ImportRowEntity.find { ImportRowTable.importId eq importId }
            .orderBy(ImportRowTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun updateStatus(id: Int, status: ImportStatus): Import? = transaction {
        ImportEntity.findById(id)?.apply { this.status = status }?.toModel()
    }
}
