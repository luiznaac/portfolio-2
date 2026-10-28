package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.catalog.CatalogService
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
import dev.agner.portfolio.usecase.institution.model.normalizedInstitutionName
import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import org.springframework.stereotype.Service

@Service
class ImportService(
    private val repository: IImportRepository,
    private val tradeService: TradeService,
    private val institutionService: InstitutionService,
    private val catalogService: CatalogService,
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

    // Apply materializes the reviewed rows into the ledger: each error-free row becomes a trade
    // unless reconciliation says the ledger already holds it. Unknown tickers auto-enter the
    // catalog and institutions auto-create on first sight. Rows are grouped by institution name to
    // create/fetch each institution once, and by natural tuple so the occurrence walk sees one
    // tuple's rows together. A manual trade the import matches is skipped and marked confirmed by
    // B3; an occurrence the ledger already holds is skipped (dedup). Flagged rows are skipped, so a
    // row-level problem never blocks the batch. The whole batch runs in one transaction, so a
    // failure part way through cannot leave a half-applied ledger behind a still-REVIEWED import.
    suspend fun apply(id: Int): Import? {
        val import = repository.fetchById(id) ?: return null
        requireStatus(import, ImportStatus.REVIEWED)

        return transaction.execute {
            val errorFreeRows = repository.fetchRows(id).filter { it.error == null }
            // Auto-enter unknown tickers into the catalog before creating trades.
            errorFreeRows.map { it.ticker }.distinct().forEach { ticker ->
                catalogService.upsertTicker(ticker)
            }

            // Each institution is created/fetched once from its group's own normalized key — the
            // rows in that group map through the institution id the key resolved to.
            val creations = errorFreeRows
                .groupBy { it.institutionName.normalizedInstitutionName() }
                .flatMap { (name, rows) ->
                    val institution = institutionService.create(InstitutionCreation(name))
                    rows.map { it.toCreation(institution.id) }
                }

            creations
                .groupBy { it.naturalKey() }
                .forEach { (key, grouped) -> tradeService.reconcile(key, grouped) }

            repository.updateStatus(id, ImportStatus.APPLIED)
        }
    }

    private fun ImportRow.toCreation(institutionId: Int) = TradeCreation(
        ticker = ticker,
        side = side,
        date = requireNotNull(date) { "Import row $id is error-free but has no date" },
        venue = venue,
        quantity = quantity,
        unitPrice = unitPrice,
        institutionId = institutionId,
    )

    private fun requireStatus(import: Import, expected: ImportStatus) {
        if (import.status != expected) throw InvalidImportStateException(import.id, expected, import.status)
    }
}
