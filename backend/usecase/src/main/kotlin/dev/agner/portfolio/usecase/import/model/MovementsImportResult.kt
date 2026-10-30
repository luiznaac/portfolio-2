package dev.agner.portfolio.usecase.import.model

import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction

// The outcome of one Movimentações upload: how many rows each destination absorbed, plus the
// corporate-action rows the log now holds (the review queue is the subset with NEEDS_REVIEW).
// `tradeImport` is the import the file's trade rows staged or matched, and `reuploaded` says its
// hash was already known — enough for the page to say "already imported" when every count is zero
// instead of showing a silent all-zero summary. A file with no trade rows has no trade import.
data class MovementsImportResult(
    val trades: Int,
    val incomes: Int,
    val corporateActions: List<CorporateAction>,
    val tradeImport: Import? = null,
    val reuploaded: Boolean = false,
)
