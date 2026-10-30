package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportCreation
import dev.agner.portfolio.usecase.import.model.ImportRow
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.ImportRowSituation
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.import.model.StagingResult
import dev.agner.portfolio.usecase.import.repository.IImportRepository
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.institution.model.normalizedInstitutionName
import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeKey
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class ImportService(
    private val repository: IImportRepository,
    private val tradeService: TradeService,
    private val institutionService: InstitutionService,
    private val catalogService: CatalogService,
    private val transaction: ITransactionTemplate,
) {

    // Re-importing the identical file is a no-op: the hash is the file's identity, so the existing
    // import (whatever its status) is returned untouched rather than staged again, and `created`
    // tells the caller whether this request made it.
    suspend fun stage(
        source: ImportSource,
        fileName: String,
        fileHash: String,
        rows: List<ImportRowPayload>,
    ): StagingResult = repository.fetchByHash(fileHash)
        ?.let { StagingResult(it, created = false) }
        ?: StagingResult(repository.save(ImportCreation(source, fileName, fileHash, rows)), created = true)

    suspend fun fetchAll() = repository.fetchAll()

    suspend fun fetchById(id: Int) = repository.fetchById(id)

    suspend fun fetchRows(id: Int): List<ImportRow>? =
        repository.fetchById(id)?.let { repository.fetchRows(id).withSituations() }

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

    // The review's per-row verdict, computed on read with the same natural tuple + occurrence walk
    // apply uses. The ledger-covered prefix of a tuple's file occurrences is duplicate_in_ledger;
    // the first occurrence past it is new; any later repeat of that tuple in the same file is
    // duplicate_in_file. Apply still writes the new suffix (the ordinal model keeps legitimate
    // repeated executions), so the last flag is an attention marker, not a skip. Error rows never
    // consult the ledger. A staged row names its institution while the ledger keys trades by id, so
    // the name is resolved through the same normalization apply uses — as a read, never a create.
    private suspend fun List<ImportRow>.withSituations(): List<ImportRow> {
        val institutionIds = map { it.institutionName }
            .distinct()
            .associateWith { name -> institutionService.fetchByName(name)?.id }
        // The occurrence ordinal is defined by position, so the file is walked once, in order.
        val occurrences = mutableMapOf<ImportRowKey, Int>()
        val ledgerCounts = mutableMapOf<TradeKey, Int>()

        return map { row ->
            if (row.error != null) {
                row.copy(situation = ImportRowSituation.ERROR)
            } else {
                val key = row.occurrenceKey()
                val occurrence = (occurrences[key] ?: 0) + 1
                occurrences[key] = occurrence
                val ledgerCount = row.ledgerKey(institutionIds[row.institutionName])
                    ?.let { ledgerCounts.getOrPut(it) { tradeService.countByKey(it) } }
                    ?: 0

                row.copy(
                    situation = when {
                        occurrence <= ledgerCount -> ImportRowSituation.DUPLICATE_IN_LEDGER
                        occurrence == ledgerCount + 1 -> ImportRowSituation.NEW
                        else -> ImportRowSituation.DUPLICATE_IN_FILE
                    },
                )
            }
        }
    }

    private fun ImportRow.occurrenceKey() = ImportRowKey(
        date = requireDate(),
        side = side,
        venue = venue,
        ticker = ticker.normalizedTicker(),
        quantity = quantity,
        unitPrice = unitPrice,
        institutionName = institutionName.normalizedInstitutionName(),
    )

    private fun ImportRow.ledgerKey(institutionId: Int?): TradeKey? =
        institutionId?.let { toCreation(it).naturalKey() }

    private fun ImportRow.toCreation(institutionId: Int) = TradeCreation(
        ticker = ticker,
        side = side,
        date = requireDate(),
        venue = venue,
        quantity = quantity,
        unitPrice = unitPrice,
        institutionId = institutionId,
    )

    // A row is applicable only when it is error-free, and the parser guarantees such a row has a
    // date; a stored row that breaks that invariant is a bug worth failing loudly on.
    private fun ImportRow.requireDate(): LocalDate =
        requireNotNull(date) { "Import row $id is error-free but has no date" }

    private fun requireStatus(import: Import, expected: ImportStatus) {
        if (import.status != expected) throw InvalidImportStateException(import.id, expected, import.status)
    }
}

// The occurrence identity of one staged row within its file: the natural tuple, with the
// institution kept as its normalized name because a staged row has no institution id until apply
// resolves one. Every row is read back from the same import, so quantity/price scales are uniform.
private data class ImportRowKey(
    val date: LocalDate,
    val side: TradeSide,
    val venue: String,
    val ticker: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val institutionName: String,
)
