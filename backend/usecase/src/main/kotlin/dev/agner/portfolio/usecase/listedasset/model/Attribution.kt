package dev.agner.portfolio.usecase.listedasset.model

import java.math.BigDecimal

// One stored slice of a listed asset's Position: part of the custody quantity mapped to a Strategy.
// The ticker is the natural key's first half; a ticker has at most one slice per strategy, so saving
// the same pair again replaces the quantity instead of stacking a second row.
data class Attribution(
    val ticker: String,
    val strategyId: Int,
    val quantity: BigDecimal,
)

// The write shape. The ticker is addressed by the URL path, so it is a parameter of the service and
// repository call rather than a field here. The quantity is the slice's new total, not a delta.
data class AttributionSliceCreation(
    val strategyId: Int,
    val quantity: BigDecimal,
)

// The transfer write shape: the quantity moves from the source slice to the destination slice, and
// custody never changes. Both strategies are addressed by id because the destination may be a
// Manual Strategy the ticker has not been attributed to yet.
data class AttributionTransferCreation(
    val fromStrategyId: Int,
    val toStrategyId: Int,
    val quantity: BigDecimal,
)
