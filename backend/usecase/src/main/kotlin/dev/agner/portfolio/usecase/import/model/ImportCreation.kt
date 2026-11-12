package dev.agner.portfolio.usecase.import.model

// A file to stage, with the already-parsed rows. The import and its rows are written together;
// `destination` is recorded with the file, and `original` is the uploaded byte content retained for
// provenance (null when a caller stages typed rows without a file).
data class ImportCreation(
    val source: ImportSource,
    val fileName: String,
    val fileHash: String,
    val rows: List<ImportRowPayload>,
    val destination: ImportDestination,
    val original: ByteArray? = null,
)
