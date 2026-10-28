package dev.agner.portfolio.usecase.tax.model

import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlice
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The default minimum realized loss a harvest pair must produce to be worth proposing. The ticket
// calls it the "minimum-gain gate"; the plan's benefit is the loss it banks, so the gate is on the
// loss magnitude.
val DEFAULT_HARVEST_MINIMUM_LOSS: BigDecimal = BigDecimal("500.00")

// Everything the pure harvest pass reads. `sellDate` is the day the SELL leg executes (the day the
// pass runs); the REBUY leg is always its next business day. `headroom` is what the month's R$20k
// stock-sale meter still allows, and `prices` are the latest closes. `slicesByTicker` is the
// Attribution ledger, so the sell can be drawn from a slice and the rebuy returned to it.
// `neutroSlices` never filters a candidate — harvest is explicitly allowed on NEUTRO — it only
// marks the proposal. `optOuts` are the per-ticker refusals; `acceptedTickers` already have a pair
// accepted this month and leave the suggestion list until the next month.
data class HarvestContext(
    val sellDate: LocalDate,
    val headroom: BigDecimal,
    val positions: List<TickerAveragePrice>,
    val prices: Map<String, BigDecimal>,
    val assetKinds: Map<String, AssetKind>,
    val slicesByTicker: Map<String, List<AttributionSlice>> = emptyMap(),
    val neutroSlices: Set<StrategyTicker> = emptySet(),
    val optOuts: Set<String> = emptySet(),
    val acceptedTickers: Set<String> = emptySet(),
    val minimumLoss: BigDecimal = DEFAULT_HARVEST_MINIMUM_LOSS,
)
