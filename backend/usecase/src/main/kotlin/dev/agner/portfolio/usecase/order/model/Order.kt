package dev.agner.portfolio.usecase.order.model

import java.math.BigDecimal

enum class OrderKind {
    BUY,
    SELL,

    /** No strategy targets this ticker anymore (ideal = 0) but custody is still above zero. */
    FULL_EXIT,

    /** Some strategy targets it (ideal > 0) and custody is currently zero. */
    NEW_ENTRY,
    ;

    val isSale: Boolean get() = this == SELL || this == FULL_EXIT
}

/**
 * One ticker's net order — already the residual left after [dev.agner.portfolio.usecase.order.TransferMatcher]'s
 * suggestions are (hypothetically) applied, so [quantity] is the smallest trade that actually has
 * to happen. [contributions] is informational: which strategies' deltas make up the net number.
 */
data class Order(
    val listedAssetId: Int,
    val ticker: String,
    val isFii: Boolean,
    val kind: OrderKind,
    val quantity: BigDecimal,
    val notional: BigDecimal,
    val contributions: List<StrategyDelta>,
    // A trade already exists today for this ticker in the opposite direction — executing this
    // order too would be a day trade (loses the sale exemption, taxed at 20% instead). Flagged,
    // never blocked. Day-trade proceeds never consume the R$20k exemption, so the sale-exemption
    // meter excludes day-trade orders (see SaleCeiling).
    val dayTradeRisk: Boolean,
)

data class StrategyDelta(
    val strategyId: Int,
    val strategyName: String,
    /**
     * Current (attributed) minus ideal, in shares. Positive means this strategy holds more of the
     * ticker than it should; negative, less.
     */
    val delta: BigDecimal,
)
