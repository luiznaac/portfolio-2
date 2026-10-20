package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.portfolio.model.Targets
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// Ticker-keyed money maps, named so a position and a price cannot be swapped at a call site.
typealias TickerPositions = Map<String, BigDecimal>
typealias TickerPrices = Map<String, BigDecimal>

// Everything the pure order-plan calculation needs: the open month's Capital and cash Floor, its
// Target chain (already canonical — MonthService normalizes it), the projected listed positions and
// the latest close for every ticker the chain can leaf on. Bundled as one context rather than a long
// parameter list, so a new input (RF actuals, funding budget) extends the context without reshaping
// every call.
data class OrderPlanContext(
    val period: YearMonth,
    val phase: OrderPlanPhase,
    val capital: BigDecimal,
    val floor: BigDecimal,
    val targets: Targets,
    val positions: TickerPositions,
    val prices: TickerPrices,
)
