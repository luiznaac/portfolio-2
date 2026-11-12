package dev.agner.portfolio.usecase.import.model

// What one apply did: the applied record beside the counts over its rows. `record` rather than
// `import` because the latter is a Kotlin keyword on a data-class parameter.
data class ImportApplyResult(
    val record: Import,
    val applied: Int,
    val rejected: Int,
    val skippedDuplicate: Int,
)
