package dev.agner.portfolio.usecase.import.model

// The per-source status surface: the latest attempt, the last one that applied, how many sessions
// still wait for a decision, and the failure when the latest attempt failed. A source whose latest
// attempt applied is healthy even if older attempts failed; those records stay in the log.
data class ImportSourceStatus(
    val source: ImportSource,
    val lastAttempt: ImportAttempt,
    val lastSuccess: ImportAttempt?,
    val pending: Int,
    val failure: ImportAttempt?,
)
