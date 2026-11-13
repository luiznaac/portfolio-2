package dev.agner.portfolio.usecase.import.model

import kotlinx.datetime.LocalDateTime

// The kind of broker file an import came from. Kept broad enough for the feeds the import
// pipeline serves; the row shape a source produces is the parser's business.
enum class ImportSource {
    B3_TRADES,
    B3_POSITION,
    B3_MOVEMENTS,
    XP_REPORT,
}

// The batch lifecycle: a file stages, is reviewed, then applies. FAILED is terminal for a file
// the pipeline could not stage at all.
enum class ImportStatus {
    STAGED,
    REVIEWED,
    APPLIED,
    FAILED,
}

// A parked session still waits for a decision; an applied or failed record is history.
val ImportStatus.isPending: Boolean
    get() = this == ImportStatus.STAGED || this == ImportStatus.REVIEWED

// One uploaded broker file. Re-importing the identical file is detected by `fileHash` and is a
// no-op, so the hash is the file's identity. `destination` is where an apply lands; `hasOriginal`
// says the uploaded bytes are retained for provenance and can be downloaded from the record.
data class Import(
    val id: Int,
    val source: ImportSource,
    val fileName: String,
    val fileHash: String,
    val status: ImportStatus,
    val destination: ImportDestination,
    val hasOriginal: Boolean,
    val createdAt: LocalDateTime,
)
