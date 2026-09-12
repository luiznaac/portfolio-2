package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

enum class OrderKind {
    BUY,
    SELL,
    FULL_EXIT, // no strategy wants this ticker anymore (ideal = 0), but custody > 0
    NEW_ENTRY, // some strategy wants it (ideal > 0) and custody is currently 0
}

// One ticker's net order — already the residual left over after TransferMatcher's suggestions
// are (hypothetically) applied, so quantity is the smallest trade that actually needs to happen.
// contributions is informational: which strategies' deltas make up this net number.
data class Order(
    val listedAssetId: Int,
    val ticker: String,
    val isFii: Boolean,
    val kind: OrderKind,
    val quantity: BigDecimal,
    val notional: BigDecimal,
    val contributions: List<StrategyDelta>,
    // A trade already exists today for this ticker in the opposite direction — executing this
    // order too would be a day trade (loses the sale-exemption, taxed at 20% instead). Flagged,
    // never blocked — see the plan's "Fases" §3.
    val dayTradeRisk: Boolean,
)

data class StrategyDelta(
    val strategyId: Int,
    val strategyName: String,
    // current (attributed) - ideal, in shares. Positive = this strategy is holding more than it
    // should for this ticker; negative = less.
    val delta: BigDecimal,
)
