package dev.agner.portfolio.usecase.strategy.model

import kotlinx.datetime.LocalDate

// One imported broker report for a strategy, at a point in time. Immutable once saved: a corrected
// report becomes a new edition rather than an overwrite, so the history — and the diff between
// editions — stays intact. See StrategyEditionService.
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
