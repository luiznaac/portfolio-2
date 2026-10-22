package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.order.model.TickerOrderPlan
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// Ticker-keyed money maps, named so a position and a price cannot be swapped at a call site.
typealias TickerPositions = Map<String, BigDecimal>
typealias TickerPrices = Map<String, BigDecimal>
typealias TickerSlices = Map<String, List<TickerOrderPlan>>

// Everything the pure order-plan calculation needs: the open month's Capital and cash Floor, its
// Target chain (already canonical — MonthService normalizes it), the projected listed positions and
// the latest close for every ticker the chain can leaf on. Bundled as one context rather than a long
// parameter list, so a new input extends the context without reshaping every call.
//
// `neutroSlices` is the Edition Entries flagged "não operar" and `attributionBySlice` maps each
// (strategy, ticker) slice to its stored Attribution quantity — the only way to size a NEUTRO slice's
// sell. The funding pass's cash input lives in FundingContext, so a phase that does not fund never
// touches the whole-book valuation.
data class OrderPlanContext(
    val period: YearMonth,
    val phase: OrderPlanPhase,
    val capital: BigDecimal,
    val floor: BigDecimal,
    val targets: Targets,
    val positions: TickerPositions,
    val prices: TickerPrices,
    val neutroSlices: Set<StrategyTicker> = emptySet(),
    val attributionBySlice: Map<StrategyTicker, BigDecimal> = emptyMap(),
)

// The funding pass's inputs, bundled so the cascade keeps the repo's context-object shape: the
// candidates to fund, every leaf slice (NEUTRO flags and attribution included), the custody and
// closes they price against, and the cash the Uninvested Balance leaves above the Floor.
data class FundingContext(
    val candidates: List<OrderCandidate>,
    val slices: List<TickerOrderPlan>,
    val positions: TickerPositions,
    val prices: TickerPrices,
    val floor: BigDecimal,
    val uninvestedBalance: BigDecimal,
)
