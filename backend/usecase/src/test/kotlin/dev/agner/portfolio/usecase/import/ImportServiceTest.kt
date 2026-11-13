package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.catalog.CatalogService
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
import dev.agner.portfolio.usecase.import.repository.IImportRepository
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.Institution
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.CustodySnapshotService
import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.CustodyPosition
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

class ImportServiceTest : StringSpec({
    val repository = mockk<IImportRepository>(relaxed = true)
    val tradeService = mockk<TradeService>(relaxed = true)
    val institutionService = mockk<InstitutionService>(relaxed = true)
    val catalogService = mockk<CatalogService>(relaxed = true)
    val custodySnapshot = mockk<CustodySnapshotService>(relaxed = true)
    val transaction = CountingTransaction()
    val service = ImportService(
        repository,
        tradeService,
        institutionService,
        catalogService,
        custodySnapshot,
        transaction,
    )

    beforeTest {
        clearAllMocks()
        transaction.count = 0
    }

    "stage should return the existing import untouched and report it was not created" {
        coEvery { repository.fetchByHash(HASH) } returns appliedImport

        service.stage(ImportSource.B3_TRADES, "neg.xlsx", HASH, listOf(payload), destination) shouldBe
            StagingResult(appliedImport, created = false)

        coVerify(exactly = 0) { repository.save(any()) }
    }

    "stage should persist a new staged import with its destination, original and rows" {
        coEvery { repository.fetchByHash(HASH) } returns null
        val captured = slot<ImportCreation>()
        coEvery { repository.save(capture(captured)) } returns stagedImport

        service.stage(ImportSource.B3_TRADES, "neg.xlsx", HASH, listOf(payload), destination, ORIGINAL) shouldBe
            StagingResult(stagedImport, created = true)

        captured.captured.let { creation ->
            creation.source shouldBe ImportSource.B3_TRADES
            creation.fileName shouldBe "neg.xlsx"
            creation.fileHash shouldBe HASH
            creation.rows shouldBe listOf(payload)
            creation.destination shouldBe destination
            creation.original!!.toList() shouldBe ORIGINAL.toList()
        }
    }

    "stage should refuse a destination whose pipeline has not landed" {
        coEvery { repository.fetchByHash(HASH) } returns null

        shouldThrow<UnsupportedImportDestinationException> {
            service.stage(
                ImportSource.B3_MOVEMENTS,
                "mov.xlsx",
                HASH,
                listOf(payload),
                ImportDestination(ImportDestinationObject.INCOME),
            )
        }

        coVerify(exactly = 0) { repository.save(any()) }
    }

    "stage should accept a custody snapshot for the POSITIONS pipeline" {
        coEvery { repository.fetchByHash(HASH) } returns null
        val captured = slot<ImportCreation>()
        coEvery { repository.save(capture(captured)) } returns stagedImport

        service.stage(
            ImportSource.B3_POSITION,
            "posicao.xlsx",
            HASH,
            listOf(payload),
            ImportDestination(ImportDestinationObject.POSITIONS),
        ) shouldBe StagingResult(stagedImport, created = true)

        captured.captured.destination shouldBe ImportDestination(ImportDestinationObject.POSITIONS)
    }

    "review should move a staged import to reviewed" {
        coEvery { repository.fetchById(1) } returns stagedImport
        coEvery { repository.updateStatus(1, ImportStatus.REVIEWED) } returns reviewedImport

        service.review(1) shouldBe reviewedImport

        coVerify(exactly = 1) { repository.updateStatus(1, ImportStatus.REVIEWED) }
    }

    "review should reject an import that is not staged" {
        coEvery { repository.fetchById(1) } returns appliedImport

        shouldThrow<InvalidImportStateException> { service.review(1) }

        coVerify(exactly = 0) { repository.updateStatus(any(), any()) }
    }

    "review should return null when the import does not exist" {
        coEvery { repository.fetchById(1) } returns null

        service.review(1) shouldBe null
    }

    "apply should reconcile one error-free row with its own trade and count the rejected row" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns listOf(row, errorRow)
        coEvery { institutionService.create(InstitutionCreation("XP INVESTIMENTOS")) } returns institution
        coEvery { repository.updateStatus(1, ImportStatus.APPLIED) } returns appliedImport

        service.apply(1) shouldBe ImportApplyResult(
            record = appliedImport,
            applied = 1,
            rejected = 1,
            skippedDuplicate = 0,
        )

        coVerify(exactly = 1) {
            tradeService.reconcile(
                any(),
                match {
                    it.size == 1 &&
                        it.single().ticker == "PETR4" &&
                        it.single().side == TradeSide.BUY &&
                        it.single().institutionId == 3 &&
                        it.single().quantity == BigDecimal("100")
                },
            )
        }
        coVerify(exactly = 1) { repository.updateStatus(1, ImportStatus.APPLIED) }
    }

    "apply should persist each row's outcome so rejected rows survive as a retry batch" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns
            listOf(row, row.copy(id = 12), row.copy(id = 13), errorRow)
        coEvery { institutionService.create(any()) } returns institution
        coEvery { tradeService.countByKey(any()) } returns 1
        coEvery { repository.updateStatus(1, ImportStatus.APPLIED) } returns appliedImport

        service.apply(1) shouldBe ImportApplyResult(
            record = appliedImport,
            applied = 2,
            rejected = 1,
            skippedDuplicate = 1,
        )

        coVerify(exactly = 1) {
            repository.saveOutcomes(
                1,
                mapOf(
                    10 to ImportRowOutcome.SKIPPED_DUPLICATE,
                    12 to ImportRowOutcome.APPLIED,
                    13 to ImportRowOutcome.APPLIED,
                    11 to ImportRowOutcome.REJECTED,
                ),
            )
        }
    }

    "apply should create/fetch each institution once for rows that share its name" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns listOf(row, row.copy(id = 12, ticker = "VALE3"))
        coEvery { institutionService.create(InstitutionCreation("XP INVESTIMENTOS")) } returns institution
        coEvery { repository.updateStatus(1, ImportStatus.APPLIED) } returns appliedImport

        service.apply(1)

        coVerify(exactly = 1) { institutionService.create(InstitutionCreation("XP INVESTIMENTOS")) }
        coVerify(exactly = 2) { tradeService.reconcile(any(), any()) }
    }

    "apply should group rows that normalize to the same natural tuple into one reconciliation" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns
            listOf(row, row.copy(id = 12, institutionName = "xp  investimentos"))
        coEvery { institutionService.create(InstitutionCreation("XP INVESTIMENTOS")) } returns institution
        coEvery { repository.updateStatus(1, ImportStatus.APPLIED) } returns appliedImport

        service.apply(1)

        coVerify(exactly = 1) { institutionService.create(any()) }
        coVerify(exactly = 1) { tradeService.reconcile(any(), match { it.size == 2 }) }
    }

    "apply should upsert each distinct error-free ticker exactly once" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns
            listOf(
                row,
                row.copy(id = 12, ticker = "PETR4"),
                row.copy(id = 13, ticker = "VALE3"),
                row.copy(id = 14, ticker = "MGLU3", error = "flagged"),
            )
        coEvery { institutionService.create(any()) } returns institution
        coEvery { repository.updateStatus(1, ImportStatus.APPLIED) } returns appliedImport

        service.apply(1)

        coVerify(exactly = 1) { catalogService.upsertTicker("PETR4") }
        coVerify(exactly = 1) { catalogService.upsertTicker("VALE3") }
        coVerify(exactly = 0) { catalogService.upsertTicker("MGLU3") }
    }

    "apply should reject an import that is not reviewed" {
        coEvery { repository.fetchById(1) } returns stagedImport

        shouldThrow<InvalidImportStateException> { service.apply(1) }

        coVerify(exactly = 0) { tradeService.reconcile(any(), any()) }
    }

    "apply should refuse a record whose destination has no pipeline" {
        coEvery { repository.fetchById(1) } returns
            reviewedImport.copy(destination = ImportDestination(ImportDestinationObject.INCOME))

        shouldThrow<UnsupportedImportDestinationException> { service.apply(1) }

        coVerify(exactly = 0) { tradeService.reconcile(any(), any()) }
        coVerify(exactly = 0) { repository.updateStatus(any(), any()) }
    }

    "apply should record a custody snapshot and mark its rows applied" {
        val custodyImport = reviewedImport.copy(
            destination = ImportDestination(ImportDestinationObject.POSITIONS),
        )
        val appliedCustody = custodyImport.copy(status = ImportStatus.APPLIED)
        val snapshot = slot<List<CustodyPosition>>()
        coEvery { repository.fetchById(1) } returns custodyImport
        coEvery { repository.fetchRows(1) } returns listOf(row, errorRow)
        coEvery { repository.updateStatus(1, ImportStatus.APPLIED) } returns appliedCustody
        coEvery { custodySnapshot.apply(capture(snapshot)) } returns Unit

        service.apply(1) shouldBe ImportApplyResult(
            record = appliedCustody,
            applied = 1,
            rejected = 1,
            skippedDuplicate = 0,
        )

        snapshot.captured shouldBe listOf(CustodyPosition("PETR4", BigDecimal("100")))
        coVerify(exactly = 1) { catalogService.upsertTicker("PETR4") }
        coVerify(exactly = 0) { tradeService.reconcile(any(), any()) }
    }

    "apply should run the whole batch inside one transaction" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns listOf(row)
        coEvery { repository.updateStatus(1, ImportStatus.APPLIED) } returns appliedImport

        service.apply(1)

        transaction.count shouldBe 1
    }

    "fetchAll should forward the status and source filters to the repository" {
        coEvery { repository.fetchAll(ImportStatus.STAGED, ImportSource.B3_TRADES) } returns
            listOf(stagedImport)

        service.fetchAll(ImportStatus.STAGED, ImportSource.B3_TRADES) shouldBe listOf(stagedImport)
    }

    "fetchSourceStatus should read the latest attempt, last success and pending count per source" {
        coEvery { repository.fetchAll(null, null) } returns
            listOf(
                appliedImport.copy(id = 1, source = ImportSource.B3_TRADES),
                stagedImport.copy(id = 2, source = ImportSource.B3_TRADES),
                reviewedImport.copy(id = 3, source = ImportSource.B3_TRADES),
                failedImport.copy(id = 4, source = ImportSource.B3_TRADES),
                appliedImport.copy(id = 5, source = ImportSource.XP_REPORT),
            )

        service.fetchSourceStatus() shouldBe listOf(
            ImportSourceStatus(
                source = ImportSource.B3_TRADES,
                lastAttempt = ImportAttempt(4, "neg.xlsx", ImportStatus.FAILED, LATER_AT),
                lastSuccess = ImportAttempt(1, "neg.xlsx", ImportStatus.APPLIED, STAGED_AT),
                pending = 2,
                failure = ImportAttempt(4, "neg.xlsx", ImportStatus.FAILED, LATER_AT),
            ),
            ImportSourceStatus(
                source = ImportSource.XP_REPORT,
                lastAttempt = ImportAttempt(5, "neg.xlsx", ImportStatus.APPLIED, STAGED_AT),
                lastSuccess = ImportAttempt(5, "neg.xlsx", ImportStatus.APPLIED, STAGED_AT),
                pending = 0,
                failure = null,
            ),
        )
    }

    "fetchSourceStatus should leave a source healthy when the latest attempt applied" {
        coEvery { repository.fetchAll(null, null) } returns
            listOf(
                failedImport.copy(id = 1, source = ImportSource.B3_TRADES),
                appliedImport.copy(id = 2, source = ImportSource.B3_TRADES, createdAt = LATER_AT),
            )

        service.fetchSourceStatus() shouldBe listOf(
            ImportSourceStatus(
                source = ImportSource.B3_TRADES,
                lastAttempt = ImportAttempt(2, "neg.xlsx", ImportStatus.APPLIED, LATER_AT),
                lastSuccess = ImportAttempt(2, "neg.xlsx", ImportStatus.APPLIED, LATER_AT),
                pending = 0,
                failure = null,
            ),
        )
    }

    "fetchSourceStatus should return an empty list when nothing was ever staged" {
        coEvery { repository.fetchAll(null, null) } returns emptyList()

        service.fetchSourceStatus() shouldBe emptyList()
    }

    "fetchRows should annotate new rows, in-file repeats and errors" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns
            listOf(row, row.copy(id = 12), errorRow, row.copy(id = 13, ticker = "VALE3"))
        coEvery { institutionService.fetchByName("XP INVESTIMENTOS") } returns institution
        coEvery { tradeService.countByKey(any()) } returns 0

        service.fetchRows(1)!!.map { it.situation } shouldBe
            listOf(
                ImportRowSituation.NEW,
                ImportRowSituation.DUPLICATE_IN_FILE,
                ImportRowSituation.ERROR,
                ImportRowSituation.NEW,
            )
    }

    "fetchRows should mark the occurrences the ledger already holds, then the new ones" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns
            listOf(row, row.copy(id = 12), row.copy(id = 13))
        coEvery { institutionService.fetchByName("XP INVESTIMENTOS") } returns institution
        coEvery { tradeService.countByKey(any()) } returns 0
        coEvery { tradeService.countByKey(match { it.ticker == "PETR4" }) } returns 1

        service.fetchRows(1)!!.map { it.situation } shouldBe
            listOf(
                ImportRowSituation.DUPLICATE_IN_LEDGER,
                ImportRowSituation.NEW,
                ImportRowSituation.DUPLICATE_IN_FILE,
            )
    }

    "fetchRows should never create the institution it resolves a row against" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns listOf(row.copy(institutionName = "CORRETORA NOVA"))
        coEvery { institutionService.fetchByName("CORRETORA NOVA") } returns null

        service.fetchRows(1)!!.single().situation shouldBe ImportRowSituation.NEW

        coVerify(exactly = 0) { institutionService.create(any()) }
        coVerify(exactly = 0) { tradeService.countByKey(any()) }
    }

    "fetchRows should normalize the institution name before matching the ledger" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns listOf(row.copy(institutionName = "xp  investimentos"))
        coEvery { institutionService.fetchByName("XP INVESTIMENTOS") } returns institution
        coEvery { tradeService.countByKey(any()) } returns 1

        service.fetchRows(1)!!.single().situation shouldBe ImportRowSituation.DUPLICATE_IN_LEDGER
    }

    "fetchRows should return null when the import does not exist" {
        coEvery { repository.fetchById(1) } returns null

        service.fetchRows(1) shouldBe null
    }

    "fetchRows should not walk the trade ledger for a custody import" {
        coEvery { repository.fetchById(1) } returns
            reviewedImport.copy(destination = ImportDestination(ImportDestinationObject.POSITIONS))
        coEvery { repository.fetchRows(1) } returns listOf(row, row.copy(id = 12), errorRow)

        service.fetchRows(1)!!.map { it.situation } shouldBe
            listOf(
                ImportRowSituation.NEW,
                ImportRowSituation.NEW,
                ImportRowSituation.ERROR,
            )

        coVerify(exactly = 0) { institutionService.fetchByName(any()) }
        coVerify(exactly = 0) { tradeService.countByKey(any()) }
    }
})

private const val HASH = "a1b2c3"
private val STAGED_AT = LocalDateTime.parse("2026-10-14T10:00:00")
private val LATER_AT = LocalDateTime.parse("2026-10-15T10:00:00")
private val ORIGINAL = "the original workbook".toByteArray()
private val destination = ImportDestination(ImportDestinationObject.TRADES)

private class CountingTransaction : ITransactionTemplate {
    var count = 0

    override suspend fun <T> execute(block: suspend () -> T): T {
        count++
        return block()
    }
}

private val stagedImport = Import(
    id = 1,
    source = ImportSource.B3_TRADES,
    fileName = "neg.xlsx",
    fileHash = HASH,
    status = ImportStatus.STAGED,
    destination = destination,
    hasOriginal = true,
    createdAt = STAGED_AT,
)

private val reviewedImport = stagedImport.copy(status = ImportStatus.REVIEWED)
private val appliedImport = stagedImport.copy(status = ImportStatus.APPLIED)
private val failedImport = stagedImport.copy(status = ImportStatus.FAILED, createdAt = LATER_AT)

private val row = ImportRow(
    id = 10,
    importId = 1,
    date = LocalDate.parse("2026-10-14"),
    side = TradeSide.BUY,
    venue = "VISTA",
    ticker = "PETR4",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("48.92"),
    institutionName = "XP INVESTIMENTOS",
    error = null,
)

private val errorRow = row.copy(id = 11, error = "unrecognized side 'X'")

private val institution = Institution(id = 3, name = "XP INVESTIMENTOS")

// Also exercises that the payload type is the staging input the parsers emit.
private val payload = ImportRowPayload(
    date = LocalDate.parse("2026-10-14"),
    side = TradeSide.BUY,
    venue = "VISTA",
    ticker = "PETR4",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("48.92"),
    institutionName = "XP INVESTIMENTOS",
)
