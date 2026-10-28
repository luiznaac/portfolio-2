package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

// Where a held Ticker sits relative to every Strategy (CONTEXT.md -> Inside a Strategy). It is a
// property of the Ticker, not of a unit: a report-driven Attribution alone never moves it inside.
enum class Placement {
    INSIDE,
    OUTSIDE,
    UNCLASSIFIED,
}

// A held ticker's placement plus the units a Manual Strategy holds of it. `manualQuantity` is capped
// by custody because the Attribution ledger can outdate a trade-down.
data class PlacedTicker(
    val placement: Placement,
    val manualQuantity: BigDecimal,
)
