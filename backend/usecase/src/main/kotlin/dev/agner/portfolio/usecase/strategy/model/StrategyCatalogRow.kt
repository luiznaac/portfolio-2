package dev.agner.portfolio.usecase.strategy.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass

// The catalog read's row: the Strategy plus its latest edition, composed by the service so the
// comparison list reads both in one pass. `latestEdition` is null when the strategy never received a
// report — a real state of the read, not a variant of the persisted Strategy.
data class StrategyCatalogRow(
    val id: Int,
    val name: String,
    val assetClass: AssetClass,
    val kind: StrategyKind,
    val latestEdition: EditionSummary?,
)
