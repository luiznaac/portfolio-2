package dev.agner.portfolio.usecase.strategy.model

// Minimal for Fase 1: just registration, so attribution movements have a strategy to reference.
// StrategyEdition/StrategyTarget (per-ticker weights parsed from broker model-portfolio PDFs)
// arrive in Fase 2.
data class Strategy(
    val id: Int,
    val name: String,
)

data class StrategyCreation(
    val name: String,
)
