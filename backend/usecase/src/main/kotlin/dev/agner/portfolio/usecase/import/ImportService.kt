package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.broker.BrokerAssetAliasService
import dev.agner.portfolio.usecase.broker.model.BrokerDescriptionResolution
import dev.agner.portfolio.usecase.broker.model.normalizedBrokerDescription
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportApplyResult
import dev.agner.portfolio.usecase.import.model.ImportAttempt
import dev.agner.portfolio.usecase.import.model.ImportCreation
import dev.agner.portfolio.usecase.import.model.ImportDestination
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportRow
import dev.agner.portfolio.usecase.import.model.ImportRowOutcome
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.ImportRowSituation
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportSourceStatus
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.import.model.StagingResult
import dev.agner.portfolio.usecase.import.model.isPending
import dev.agner.portfolio.usecase.import.repository.IImportRepository
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.institution.model.normalizedInstitutionName
import dev.agner.portfolio.usecase.listedasset.CustodySnapshotService
import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.CustodyPosition
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
    private val custodySnapshot: CustodySnapshotService,
    private val brokerAssetAliasService: BrokerAssetAliasService,
    private val transaction: ITransactionTemplate,
) {

    // Re-importing the identical file is a no-op: the hash is the file's identity, so the existing
    // import (whatever its status) is returned untouched rather than staged again, and `created`
    // tells the caller whether this request made it. The destination is recorded with the file so a
    // parked session can resume and an apply can land on the object the record names. A destination
    // whose own pipeline has not landed yet is refused before anything is written.
    suspend fun stage(
        source: ImportSource,
        fileName: String,
        fileHash: String,
        rows: List<ImportRowPayload>,
        destination: ImportDestination,
        original: ByteArray? = null,
    ): StagingResult {
        repository.fetchByHash(fileHash)?.let { return StagingResult(it, created = false) }
        requireSupported(destination)
        return StagingResult(
            repository.save(
                ImportCreation(source, fileName, fileHash, resolveDescriptions(rows), destination, original),
            ),
            created = true,
        )
    }

    // A broker note prints an asset description rather than a ticker. The parser keeps the raw
    // description, and this boundary resolves it once through the Broker Asset Alias — against the
    // row's institution (a read, because apply is what creates it) and the global fallback. A
    // description no alias covers, or two that disagree, becomes a row-level error so the valid
    // rows still apply and the description stays for the owner to confirm.
    private suspend fun resolveDescriptions(rows: List<ImportRowPayload>): List<ImportRowPayload> {
        val institutionIds = rows
            .filter { it.rawDescription != null }
            .associateBy({ it.institutionName.normalizedInstitutionName() }, { it.institutionName })
            .mapValues { (_, name) -> institutionService.fetchByName(name)?.id }

        return rows.map { row ->
            val raw = row.rawDescription ?: return@map row
            val institutionId = institutionIds[row.institutionName.normalizedInstitutionName()]
            val resolution = brokerAssetAliasService.resolve(
                institutionId,
                raw.normalizedBrokerDescription(),
            )
            val resolvedTicker = when (resolution) {
                is BrokerDescriptionResolution.Resolved -> resolution.ticker.normalizedTicker()
                BrokerDescriptionResolution.Unresolved, is BrokerDescriptionResolution.Ambiguous ->
                    row.ticker
            }
            row.copy(ticker = resolvedTicker, error = row.error ?: descriptionError(raw, resolution))
        }
    }

    // The owner's answer for a description the review could not resolve. The alias lands on the
    // row's institution (created on first sight), so the next note from that broker resolves
    // silently, and the row becomes applicable. A parser error the row already carried stays: the
    // confirmation answers the description, nothing else.
    suspend fun confirmTicker(importId: Int, rowId: Int, ticker: String): ImportRow? {
        val import = repository.fetchById(importId) ?: return null
        requirePending(import)
        val row = repository.fetchRows(importId).find { it.id == rowId } ?: return null
        val rawDescription = row.rawDescription
            ?.takeIf { row.ticker == null }
            ?: throw ImportRowNotConfirmableException(rowId)
        val normalizedTicker = ticker.normalizedTicker()
        if (normalizedTicker.isBlank() || normalizedTicker.length > MAX_TICKER_LENGTH) {
            throw InvalidImportRowTickerException(ticker)
        }
        val institution = institutionService.create(InstitutionCreation(row.institutionName))

        // The alias and the row move together: a failed row update must not leave an alias the
        // review never confirmed.
        transaction.execute {
            brokerAssetAliasService.confirm(
                institutionId = institution.id,
                normalizedDescription = rawDescription.normalizedBrokerDescription(),
                ticker = normalizedTicker,
            )
            repository.updateRowTicker(
                importId = importId,
                rowId = rowId,
                ticker = normalizedTicker,
                error = if (row.descriptionErrorAnswered()) null else row.error,
            )
        }
        return fetchRows(importId)?.find { it.id == rowId }
    }

    suspend fun fetchAll(status: ImportStatus? = null, source: ImportSource? = null) =
        repository.fetchAll(status, source)

    // The per-source read behind the sources screen. Only a source with attempts gets an entry, so
    // an empty log is an empty list. The latest attempt is the newest record by timestamp (id breaks
    // a tie); the failure is that same attempt when it failed, never a stale one the source has
    // since outgrown. Pending counts the sessions still waiting for a decision.
    suspend fun fetchSourceStatus(): List<ImportSourceStatus> =
        repository.fetchAll()
            .groupBy { it.source }
            .map { (source, records) ->
                val newestFirst = records.sortedWith(
                    compareByDescending<Import> { it.createdAt }.thenByDescending { it.id },
                )
                val lastAttempt = newestFirst.first().toAttempt()
                ImportSourceStatus(
                    source = source,
                    lastAttempt = lastAttempt,
                    lastSuccess = newestFirst.firstOrNull { it.status == ImportStatus.APPLIED }?.toAttempt(),
                    pending = records.count { it.status.isPending },
                    failure = lastAttempt.takeIf { it.status == ImportStatus.FAILED },
                )
            }
            .sortedBy { it.source.ordinal }

    suspend fun fetchById(id: Int) = repository.fetchById(id)

    suspend fun fetchOriginal(id: Int) = repository.fetchOriginal(id)

    suspend fun fetchRows(id: Int): List<ImportRow>? =
        repository.fetchById(id)?.let { import ->
            val rows = repository.fetchRows(id)
            // The trade occurrence walk only means something for a ledger import; a custody row is
            // a snapshot line, so its situation is `new` (or `error`) by shape.
            when (import.destination.objectType) {
                ImportDestinationObject.POSITIONS -> rows.map { row ->
                    row.copy(
                        situation = if (row.error != null) {
                            ImportRowSituation.ERROR
                        } else {
                            ImportRowSituation.NEW
                        },
                    )
                }
                else -> rows.withSituations()
            }
        }

    suspend fun review(id: Int): Import? {
        val import = repository.fetchById(id) ?: return null
        requireStatus(import, ImportStatus.STAGED)
        return repository.updateStatus(id, ImportStatus.REVIEWED)
    }

    // Apply materializes the reviewed rows into the destination named by the record. A ledger
    // import writes trades; a custody snapshot overwrites positions and opens a GAP per divergence.
    suspend fun apply(id: Int): ImportApplyResult? {
        val import = repository.fetchById(id) ?: return null
        requireStatus(import, ImportStatus.REVIEWED)

        return when (import.destination.objectType) {
            ImportDestinationObject.TRADES -> applyTrades(import)
            ImportDestinationObject.POSITIONS -> applyCustody(import)
            else -> throw UnsupportedImportDestinationException(import.destination)
        }
    }

    // Each error-free row becomes a trade unless reconciliation says the ledger already holds it.
    // Unknown tickers auto-enter the catalog and institutions auto-create on first sight. Rows are
    // grouped by institution name to create/fetch each institution once, and by natural tuple so
    // the occurrence walk sees one tuple's rows together. A manual trade the import matches is
    // skipped and marked confirmed by B3; an occurrence the ledger already holds is skipped (dedup).
    // Flagged rows are skipped, so a row-level problem never blocks the batch. Every row's outcome
    // is persisted, so the rejected rows stay visible as the retry batch. The whole batch runs in one
    // transaction, so a failure part way through cannot leave a half-applied ledger behind a
    // still-REVIEWED import.
    private suspend fun applyTrades(import: Import): ImportApplyResult = transaction.execute {
        val rows = repository.fetchRows(import.id).withSituations()
        val errorFreeRows = rows.filter { it.error == null }
        // Auto-enter unknown tickers into the catalog before creating trades.
        errorFreeRows.map { it.requireTicker() }.distinct().forEach { ticker ->
            catalogService.upsertTicker(ticker)
        }

        // Each institution is created/fetched once from its group's own normalized key — the
        // rows in that group map through the institution id the key resolved to.
        val creations = errorFreeRows
            .groupBy { it.institutionName.normalizedInstitutionName() }
            .flatMap { (name, grouped) ->
                val institution = institutionService.create(InstitutionCreation(name))
                grouped.map { it.toCreation(institution.id) }
            }

        creations
            .groupBy { it.naturalKey() }
            .forEach { (key, grouped) -> tradeService.reconcile(key, grouped) }

        completeApply(import.id, rows.outcomes())
    }

    // A custody snapshot is state-like: every error-free line overwrites its ticker's position and
    // a delta the ledger cannot explain opens a GAP. A divergence never blocks the batch — the GAP
    // is the reconciliation queue (Ativos · Lacunas), not a failure.
    private suspend fun applyCustody(import: Import): ImportApplyResult = transaction.execute {
        val rows = repository.fetchRows(import.id)
        val errorFreeRows = rows.filter { it.error == null }
        errorFreeRows.map { it.requireTicker() }.distinct().forEach { ticker ->
            catalogService.upsertTicker(ticker)
        }

        custodySnapshot.apply(errorFreeRows.map { CustodyPosition(it.requireTicker(), it.quantity) })

        completeApply(
            import.id,
            rows.associate { row ->
                row.id to if (row.error == null) ImportRowOutcome.APPLIED else ImportRowOutcome.REJECTED
            },
        )
    }

    // The shared tail of an apply: persist each row's decision, mark the record applied and report
    // the counts over those decisions.
    private suspend fun completeApply(
        importId: Int,
        outcomes: Map<Int, ImportRowOutcome>,
    ): ImportApplyResult {
        repository.saveOutcomes(importId, outcomes)
        val applied = repository.updateStatus(importId, ImportStatus.APPLIED)
        return ImportApplyResult(
            record = requireNotNull(applied) { "Import $importId disappeared during apply" },
            applied = outcomes.count { it.value == ImportRowOutcome.APPLIED },
            rejected = outcomes.count { it.value == ImportRowOutcome.REJECTED },
            skippedDuplicate = outcomes.count { it.value == ImportRowOutcome.SKIPPED_DUPLICATE },
        )
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

    // Apply's persisted decision per row. In-file repeats are written by the ordinal model, so they
    // are applied; the ledger-covered prefix is the duplicate that was ignored.
    private fun List<ImportRow>.outcomes(): Map<Int, ImportRowOutcome> = associate { row ->
        row.id to when (requireNotNull(row.situation) { "Import row ${row.id} has no situation" }) {
            ImportRowSituation.NEW, ImportRowSituation.DUPLICATE_IN_FILE -> ImportRowOutcome.APPLIED
            ImportRowSituation.DUPLICATE_IN_LEDGER -> ImportRowOutcome.SKIPPED_DUPLICATE
            ImportRowSituation.ERROR -> ImportRowOutcome.REJECTED
        }
    }

    private fun ImportRow.occurrenceKey() = ImportRowKey(
        date = requireDate(),
        side = side,
        venue = venue,
        ticker = requireTicker().normalizedTicker(),
        quantity = quantity,
        unitPrice = unitPrice,
        institutionName = institutionName.normalizedInstitutionName(),
    )

    private fun ImportRow.ledgerKey(institutionId: Int?): TradeKey? =
        institutionId?.let { toCreation(it).naturalKey() }

    private fun ImportRow.toCreation(institutionId: Int) = TradeCreation(
        ticker = requireTicker(),
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

    // An error-free row has resolved its ticker (a broker note's through the alias, a ticker layout's
    // at parse); a row that breaks that invariant is a bug worth failing loudly on.
    private fun ImportRow.requireTicker(): String =
        requireNotNull(ticker) { "Import row $id is error-free but has no ticker" }

    // The error a description that did not resolve leaves on its row. A confirmation answers exactly
    // that error; any other row-level problem stays, so the row is still flagged for the retry batch.
    private fun ImportRow.descriptionErrorAnswered(): Boolean =
        rawDescription != null &&
            error?.startsWith("$DESCRIPTION_ERROR_PREFIX$rawDescription'") == true

    private fun descriptionError(
        raw: String,
        resolution: BrokerDescriptionResolution,
    ): String? = when (resolution) {
        is BrokerDescriptionResolution.Resolved -> null
        BrokerDescriptionResolution.Unresolved ->
            "${DESCRIPTION_ERROR_PREFIX}$raw' did not resolve to a ticker"
        is BrokerDescriptionResolution.Ambiguous ->
            "${DESCRIPTION_ERROR_PREFIX}$raw' is ambiguous: " +
                resolution.tickers.sorted().joinToString(", ")
    }

    private fun Import.toAttempt() = ImportAttempt(id, fileName, status, createdAt)

    private fun requireStatus(import: Import, expected: ImportStatus) {
        if (import.status != expected) throw InvalidImportStateException(import.id, expected, import.status)
    }

    // A review edit is legal while the session still waits for its decision; an applied or failed
    // record is history.
    private fun requirePending(import: Import) {
        if (!import.status.isPending) {
            throw InvalidImportStateException(import.id, PENDING_STATUSES, import.status)
        }
    }

    // Only the destinations whose own pipeline has landed can stage and apply; the typed destination
    // keeps the others nameable so their own slices can add the pipeline without a schema change.
    private fun requireSupported(destination: ImportDestination) {
        if (destination.objectType !in SUPPORTED_DESTINATIONS) {
            throw UnsupportedImportDestinationException(destination)
        }
    }
}

private val SUPPORTED_DESTINATIONS = setOf(
    ImportDestinationObject.TRADES,
    ImportDestinationObject.POSITIONS,
)

private val PENDING_STATUSES = setOf(ImportStatus.STAGED, ImportStatus.REVIEWED)

// The width both the row's ticker and the Broker Asset Alias ticker store, so an answer that would
// overflow the column is the caller's error rather than a database failure.
private const val MAX_TICKER_LENGTH = 12

// The prefix every description error the alias lookup leaves carries, so a confirmation can clear
// exactly that error and leave a parser error in place.
private const val DESCRIPTION_ERROR_PREFIX = "Description '"

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
