package dev.agner.portfolio.usecase.strategy.model

// A Strategy × ticker pair — the granularity of an Edition Entry, and therefore of the "não operar"
// (NEUTRO) constraint. The Strategy is referenced by name until the entity is first-class in a Target
// chain, matching StrategyTarget.
data class StrategyTicker(
    val strategy: String,
    val ticker: String,
)
