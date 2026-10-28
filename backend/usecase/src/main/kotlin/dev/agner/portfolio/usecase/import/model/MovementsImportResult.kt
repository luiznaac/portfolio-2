package dev.agner.portfolio.usecase.import.model

import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction

// The outcome of one Movimentações upload: how many rows each destination absorbed, plus the
// corporate-action rows the log now holds (the review queue is the subset with NEEDS_REVIEW).
data class MovementsImportResult(
    val trades: Int,
    val incomes: Int,
    val corporateActions: List<CorporateAction>,
)
