package dev.agner.portfolio.usecase.strategy.model

import kotlinx.datetime.LocalDate

// One imported XP report for a strategy, at a point in time (competência). Immutable once saved —
// a corrected report is a new edition, never an overwrite, so the history (and the diff between
// editions) stays intact. See StrategyEditionService.
data class StrategyEdition(
    val id: Int,
    val strategyId: Int,
    val referenceDate: LocalDate,
    val changesText: String?,
    val targets: List<StrategyTarget>,
)

data class StrategyEditionCreation(
    val strategyId: Int,
    val referenceDate: LocalDate,
    val changesText: String?,
    val targets: List<StrategyTarget>,
)
