package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.order.model.PlacedTicker
import dev.agner.portfolio.usecase.order.model.Placement
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import java.math.BigDecimal

// The two predicates every consumer reads instead of re-deriving the verdict from the enum.
internal fun TickerPlacements.isOutside(ticker: String): Boolean =
    this[ticker]?.placement == Placement.OUTSIDE

internal fun TickerPlacements.isUnclassified(ticker: String): Boolean =
    this[ticker]?.placement == Placement.UNCLASSIFIED

// The single placement seam the order plan and the funding pass both read. A ticker is inside when a
// Target names it with weight > 0 or a Manual Strategy's Attribution holds at least one of its
// units; a report-driven Attribution is inert. A ticker the catalog has not classified is its own
// data-quality state — it belongs to no class or Strategy rung and is never an outside holding.
//
// `targetedTickers` is the report-membership half, read from the chain leaves by the caller, so the
// two halves cannot disagree about a Target the chain does not carry.
object PlacementCalculator {

    fun compute(
        positions: TickerPositions,
        targetedTickers: Set<String>,
        ledger: Map<StrategyTicker, BigDecimal>,
        manualStrategies: Set<String>,
        unclassifiedTickers: Set<String>,
    ): TickerPlacements {
        val manualByTicker = ledger
            .filterKeys { it.strategy in manualStrategies }
            .filterValues { it.signum() > 0 }
            .entries
            .groupBy({ it.key.ticker }, { it.value })

        return positions
            .filterValues { it.signum() > 0 }
            .mapValues { (ticker, position) ->
                val manual = manualByTicker[ticker].orEmpty()
                    .sumOf { it }
                    .coerceAtMost(position)
                    .coerceAtLeast(BigDecimal.ZERO)
                PlacedTicker(
                    placement = placementOf(
                        targeted = ticker in targetedTickers,
                        manual = manual.signum() > 0,
                        unclassified = ticker in unclassifiedTickers,
                    ),
                    manualQuantity = manual,
                )
            }
    }

    // An unclassified ticker wins over every other state: without a class it cannot join a Strategy
    // rung, so a Target or a stale manual slice cannot make it inside.
    private fun placementOf(targeted: Boolean, manual: Boolean, unclassified: Boolean): Placement =
        when {
            unclassified -> Placement.UNCLASSIFIED
            targeted || manual -> Placement.INSIDE
            else -> Placement.OUTSIDE
        }
}
