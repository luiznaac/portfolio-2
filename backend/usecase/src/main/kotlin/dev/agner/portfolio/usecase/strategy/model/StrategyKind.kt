package dev.agner.portfolio.usecase.strategy.model

// The two kinds of Strategy. REPORT_DRIVEN is fed by an Edition and planned through Targets;
// MANUAL is the legacy bucket — no Edition, no Targets, one per Asset Class. The kind is stored,
// never inferred from the absence of an Edition: a Report Strategy is edition-less between its
// creation and its first import.
enum class StrategyKind {
    REPORT_DRIVEN,
    MANUAL,
}
