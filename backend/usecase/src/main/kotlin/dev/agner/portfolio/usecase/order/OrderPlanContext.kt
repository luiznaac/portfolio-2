package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.order.model.PlacedTicker
import dev.agner.portfolio.usecase.order.model.TickerOrderPlan
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// Ticker-keyed money maps, named so a position and a price cannot be swapped at a call site.
typealias TickerPositions = Map<String, BigDecimal>
typealias TickerPrices = Map<String, BigDecimal>
typealias TickerSlices = Map<String, List<TickerOrderPlan>>
typealias TickerPlacements = Map<String, PlacedTicker>

// A positive close prices a ticker; an absent or non-positive one has no R$ value to work with.
fun TickerPrices.priceOf(ticker: String): BigDecimal? = this[ticker]?.takeIf { it.signum() > 0 }

// RF sub-class Actuals, keyed the way the chain addresses them.
typealias SubClassAmounts = Map<FixedIncomeSubClass, BigDecimal>

// Everything the pure order-plan calculation needs: the open month's Capital and cash Floor, its
// Target chain (already canonical — MonthService normalizes it), the projected listed positions and
// the latest close for every ticker the chain can leaf on. Bundled as one context rather than a long
// parameter list, so a new input extends the context without reshaping every call.
//
// `neutroSlices` is the Edition Entries flagged "não operar" and `attributionBySlice` maps each
// (strategy, ticker) slice to its stored Attribution quantity — the only way to size a NEUTRO slice's
// sell. `placement` is the shared inside/outside/unclassified verdict per held ticker (see
// PlacementCalculator); both this calculator and the funding pass read it instead of re-deriving the
// rule. `fixedIncomeActualBySubClass` values the RF book for the full phase's sub-class orders; not
// every phase reads it. The funding pass's cash input lives in FundingContext, so a phase that does
// not fund never touches the whole-book valuation.
data class OrderPlanContext(
    val period: YearMonth,
    val phase: OrderPlanPhase,
    val capital: BigDecimal,
    val floor: BigDecimal,
    val targets: Targets,
    val positions: TickerPositions,
    val prices: TickerPrices,
    val placement: TickerPlacements,
    val neutroSlices: Set<StrategyTicker> = emptySet(),
    val attributionBySlice: Map<StrategyTicker, BigDecimal> = emptyMap(),
    val fixedIncomeActualBySubClass: SubClassAmounts = emptyMap(),
)

// The funding pass's inputs, bundled so the cascade keeps the repo's context-object shape: the
// candidates to fund, every leaf slice (NEUTRO flags and attribution included), the custody and
// closes they price against, the placement verdict the outside and surplus pools read, and the cash
// the Uninvested Balance leaves above the Floor.
data class FundingContext(
    val candidates: List<OrderCandidate>,
    val slices: List<TickerOrderPlan>,
    val positions: TickerPositions,
    val prices: TickerPrices,
    val placement: TickerPlacements,
    val floor: BigDecimal,
    val uninvestedBalance: BigDecimal,
)
