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
