package dev.agner.portfolio.usecase.strategy.model

import kotlinx.datetime.YearMonth

// The latest Edition reduced to what a strategy's catalog row needs: no entries and no diff, so the
// list read never carries a whole portfolio. `status` is what renders the staged badge.
data class EditionSummary(
    val id: Int,
    val referenceMonth: YearMonth,
    val status: EditionStatus,
)
