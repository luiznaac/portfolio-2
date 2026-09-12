package dev.agner.portfolio.usecase.strategy.model

// Read-model for the strategy screen's "what changed" view — null diff only for a strategy's
// very first edition, which has no prior edition to compare against.
data class StrategyEditionWithDiff(
    val edition: StrategyEdition,
    val diff: StrategyTargetDiff?,
)
