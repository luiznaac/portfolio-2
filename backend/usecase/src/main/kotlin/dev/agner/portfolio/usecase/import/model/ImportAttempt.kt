package dev.agner.portfolio.usecase.import.model

import kotlinx.datetime.LocalDateTime

// One attempt at a source: the record's own identity, its state and when it was staged. A failed
// file is an attempt like any other — it keeps its own import record and is linked from here.
data class ImportAttempt(
    val importId: Int,
    val fileName: String,
    val status: ImportStatus,
    val createdAt: LocalDateTime,
)
