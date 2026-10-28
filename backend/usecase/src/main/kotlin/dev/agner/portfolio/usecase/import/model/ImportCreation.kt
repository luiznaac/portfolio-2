package dev.agner.portfolio.usecase.import.model

// A file to stage, with the already-parsed rows. The import and its rows are written together.
data class ImportCreation(
    val source: ImportSource,
    val fileName: String,
    val fileHash: String,
    val rows: List<ImportRowPayload>,
)
