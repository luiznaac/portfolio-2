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
import dev.agner.portfolio.usecase.institution.model.Institution
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.TradeService
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
    val transaction = CountingTransaction()
    val service = ImportService(repository, tradeService, institutionService, transaction)

    beforeTest {
        clearAllMocks()
        transaction.count = 0
    }

    "stage should be a no-op when the file hash was already imported" {
        coEvery { repository.fetchByHash(HASH) } returns stagedImport

        service.stage(ImportSource.B3_TRADES, "neg.xlsx", HASH, listOf(payload)) shouldBe stagedImport

        coVerify(exactly = 0) { repository.save(any()) }
    }

    "stage should persist a new staged import with its rows" {
        coEvery { repository.fetchByHash(HASH) } returns null
        val captured = slot<ImportCreation>()
        coEvery { repository.save(capture(captured)) } returns stagedImport

        service.stage(ImportSource.B3_TRADES, "neg.xlsx", HASH, listOf(payload))

        captured.captured shouldBe ImportCreation(ImportSource.B3_TRADES, "neg.xlsx", HASH, listOf(payload))
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

    "apply should create one trade per error-free row and mark the import applied" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns listOf(row, errorRow)
        coEvery { institutionService.create(InstitutionCreation("XP INVESTIMENTOS")) } returns institution
        coEvery { repository.updateStatus(1, ImportStatus.APPLIED) } returns appliedImport

        service.apply(1) shouldBe appliedImport

        coVerify(exactly = 1) {
            tradeService.create(
                match {
                    it.ticker == "PETR4" &&
                        it.side == TradeSide.BUY &&
                        it.institutionId == 3 &&
                        it.quantity == BigDecimal("100")
                },
            )
        }
        coVerify(exactly = 1) { repository.updateStatus(1, ImportStatus.APPLIED) }
    }

    "apply should create/fetch each institution once for rows that share its name" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns listOf(row, row.copy(id = 12, ticker = "VALE3"))
        coEvery { institutionService.create(InstitutionCreation("XP INVESTIMENTOS")) } returns institution
        coEvery { repository.updateStatus(1, ImportStatus.APPLIED) } returns appliedImport

        service.apply(1)

        coVerify(exactly = 1) { institutionService.create(InstitutionCreation("XP INVESTIMENTOS")) }
        coVerify(exactly = 2) { tradeService.create(any()) }
    }

    "apply should group institution names that normalize alike" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns
            listOf(row, row.copy(id = 12, institutionName = "xp  investimentos"))
        coEvery { institutionService.create(InstitutionCreation("XP INVESTIMENTOS")) } returns institution
        coEvery { repository.updateStatus(1, ImportStatus.APPLIED) } returns appliedImport

        service.apply(1)

        coVerify(exactly = 1) { institutionService.create(any()) }
        coVerify(exactly = 2) { tradeService.create(any()) }
    }

    "apply should reject an import that is not reviewed" {
        coEvery { repository.fetchById(1) } returns stagedImport

        shouldThrow<InvalidImportStateException> { service.apply(1) }

        coVerify(exactly = 0) { tradeService.create(any()) }
    }

    "apply should run the whole batch inside one transaction" {
        coEvery { repository.fetchById(1) } returns reviewedImport
        coEvery { repository.fetchRows(1) } returns listOf(row)
        coEvery { repository.updateStatus(1, ImportStatus.APPLIED) } returns appliedImport

        service.apply(1)

        transaction.count shouldBe 1
    }

    "fetchRows should return null when the import does not exist" {
        coEvery { repository.fetchById(1) } returns null

        service.fetchRows(1) shouldBe null
    }
})

private const val HASH = "a1b2c3"

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
    createdAt = LocalDateTime.parse("2026-10-14T10:00:00"),
)

private val reviewedImport = stagedImport.copy(status = ImportStatus.REVIEWED)
private val appliedImport = stagedImport.copy(status = ImportStatus.APPLIED)

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
