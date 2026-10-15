package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportCreation
import dev.agner.portfolio.usecase.import.model.ImportRow
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.import.repository.IImportRepository
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import org.springframework.stereotype.Service

@Service
class ImportService(
    private val repository: IImportRepository,
    private val tradeService: TradeService,
    private val institutionService: InstitutionService,
    private val transaction: ITransactionTemplate,
) {

    // Re-importing the identical file is a no-op: the hash is the file's identity, so the existing
    // import (whatever its status) is returned untouched rather than staged again.
    suspend fun stage(
        source: ImportSource,
        fileName: String,
        fileHash: String,
        rows: List<ImportRowPayload>,
    ): Import = repository.fetchByHash(fileHash)
        ?: repository.save(ImportCreation(source, fileName, fileHash, rows))

    suspend fun fetchAll() = repository.fetchAll()

    suspend fun fetchById(id: Int) = repository.fetchById(id)

    suspend fun fetchRows(id: Int): List<ImportRow>? =
        repository.fetchById(id)?.let { repository.fetchRows(id) }

    suspend fun review(id: Int): Import? {
        val import = repository.fetchById(id) ?: return null
        requireStatus(import, ImportStatus.STAGED)
        return repository.updateStatus(id, ImportStatus.REVIEWED)
    }

    // Apply materializes the reviewed rows into the ledger: each error-free row becomes a trade and
    // its institution is auto-created on first sight. Flagged rows are skipped, so a row-level
    // problem never blocks the batch. The whole batch runs in one transaction, so a failure part
    // way through cannot leave a half-applied ledger behind a still-REVIEWED import.
    suspend fun apply(id: Int): Import? {
        val import = repository.fetchById(id) ?: return null
        requireStatus(import, ImportStatus.REVIEWED)

        return transaction.execute {
            repository.fetchRows(id)
                .filter { it.error == null }
                .forEach { row ->
                    val institution = institutionService.create(InstitutionCreation(row.institutionName))
                    tradeService.create(
                        TradeCreation(
                            ticker = row.ticker,
                            side = row.side,
                            date = row.date,
                            venue = row.venue,
                            quantity = row.quantity,
                            unitPrice = row.unitPrice,
                            institutionId = institution.id,
                        ),
                    )
                }

            repository.updateStatus(id, ImportStatus.APPLIED)
        }
    }

    private fun requireStatus(import: Import, expected: ImportStatus) {
        if (import.status != expected) throw InvalidImportStateException(import.id, expected, import.status)
    }
}
