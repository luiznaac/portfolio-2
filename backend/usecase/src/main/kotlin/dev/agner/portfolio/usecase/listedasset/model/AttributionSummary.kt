package dev.agner.portfolio.usecase.listedasset.model

import dev.agner.portfolio.usecase.strategy.model.AttributionLedger
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
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

// The ticker-keyed slice read — the attribution ledger's source shape, named so the two projections
// (the ledger and the bulk planner) cannot drift.
typealias AttributionSlicesByTicker = Map<String, List<AttributionSlice>>

// The (strategy, ticker) ledger the placement rule and the order engine consume, projected from the
// ticker-keyed slice read — one projection, so the two reads can never disagree.
fun AttributionSlicesByTicker.toLedger(): AttributionLedger =
    flatMap { (ticker, slices) ->
        slices.map { slice -> StrategyTicker(strategy = slice.strategyName, ticker = ticker) to slice.quantity }
    }.toMap()
