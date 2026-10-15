package dev.agner.portfolio.usecase.import.repository

import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportCreation
import dev.agner.portfolio.usecase.import.model.ImportRow
import dev.agner.portfolio.usecase.import.model.ImportStatus

interface IImportRepository {

    // The hash is the file's identity — the stage idempotency check reads through it.
    suspend fun fetchByHash(fileHash: String): Import?

    suspend fun save(creation: ImportCreation): Import

    suspend fun fetchAll(): List<Import>

    suspend fun fetchById(id: Int): Import?

    suspend fun fetchRows(importId: Int): List<ImportRow>

    // Null when no import has that id.
    suspend fun updateStatus(id: Int, status: ImportStatus): Import?
}
