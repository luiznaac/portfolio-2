package dev.agner.portfolio.usecase.listedasset.model

import java.math.BigDecimal

// The attribution read for the Ativos detail: the custody quantity, the strategy slices and the
// remainder that belongs to no strategy. Unattributed is computed, never stored — the sum of the
// slices is the only ledger, and the remainder falls out of custody minus that sum.
data class AttributionSummary(
    val ticker: String,
    val custodyQuantity: BigDecimal,
    val attributedQuantity: BigDecimal,
    val unattributedQuantity: BigDecimal,
    val slices: List<AttributionSlice>,
)

// A slice joined with the strategy's name, so the browser does not have to resolve the catalog.
data class AttributionSlice(
    val strategyId: Int,
    val strategyName: String,
    val quantity: BigDecimal,
)
