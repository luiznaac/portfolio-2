package dev.agner.portfolio.usecase.import.repository

import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportCreation
import dev.agner.portfolio.usecase.import.model.ImportRow
import dev.agner.portfolio.usecase.import.model.ImportRowOutcome
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportStatus

interface IImportRepository {

    // The hash is the file's identity — the stage idempotency check reads through it.
    suspend fun fetchByHash(fileHash: String): Import?

    suspend fun save(creation: ImportCreation): Import

    suspend fun fetchAll(status: ImportStatus? = null, source: ImportSource? = null): List<Import>

    suspend fun fetchById(id: Int): Import?

    // The uploaded bytes retained at staging, or null when the import was staged without a file.
    suspend fun fetchOriginal(id: Int): ByteArray?

    suspend fun fetchRows(importId: Int): List<ImportRow>

    // Null when no import has that id.
    suspend fun updateStatus(id: Int, status: ImportStatus): Import?

    // The apply-time decision for each row, written in the same transaction as the ledger writes.
    suspend fun saveOutcomes(importId: Int, outcomes: Map<Int, ImportRowOutcome>)
}
