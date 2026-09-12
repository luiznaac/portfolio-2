package dev.agner.portfolio.usecase.strategy.model

// entered/exited keep the target as it stood on the side it's known from (new weight for
// entered, the previous edition's weight for exited — there's no "current" weight for a ticker
// that just left). changed pairs (before, after) only for tickers present in both editions whose
// weight actually moved.
data class StrategyTargetDiff(
    val entered: List<StrategyTarget>,
    val exited: List<StrategyTarget>,
    val changed: List<StrategyTargetChange>,
)

data class StrategyTargetChange(
    val ticker: String,
    val before: StrategyTarget,
    val after: StrategyTarget,
)
