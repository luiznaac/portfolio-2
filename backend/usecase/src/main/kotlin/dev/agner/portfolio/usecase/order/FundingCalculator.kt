package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.order.model.FundingSource
import dev.agner.portfolio.usecase.order.model.FundingSummary
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.TickerOrderPlan
import dev.agner.portfolio.usecase.order.model.targetUnits
import java.math.BigDecimal
import java.math.RoundingMode

// The funding pass's outcome: the SELLs it adds and the arithmetic that explains them.
data class FundingOutcome(
    val sells: List<OrderCandidate>,
    val summary: FundingSummary,
)

// The rebalancing engine's funding layer. The candidates phase spends the Uninvested Balance; when
// that is not enough it sells positions, in a fixed source order (CONTEXT.md -> Funding):
//
// 1. NEUTRO slices — the (strategy, ticker) pairs flagged "não operar", whose attributed units are
//    the least wanted money in the book;
// 2. positions outside every strategy — a held ticker no Target names, the "outside strategy"
//    holdings the spec's FII worked example describes (weight 0 everywhere). A ticker a Target names
//    is inside a strategy even when its Attribution ledger is empty; the ledger says which strategy
//    holds the units, not whether the ticker belongs to one;
// 3. the largest Surpluses by R$ — held units above the ticker's whole-unit Target.
//
// Only enough is sold to cover the shortfall, and sells floor to whole units, so a remainder stays
// unfunded rather than being bought with money the plan does not raise. The EXIT candidates already
// in the plan are sells too: their proceeds count and their units are never sold a second time.
object FundingCalculator {

    fun compute(context: FundingContext): FundingOutcome {
        val buyAmount = context.candidates
            .filter { it.side.isBuy }
            .sumOf { it.amount ?: BigDecimal.ZERO }
            .defaultScale()
        val deployableCash = (context.uninvestedBalance - context.floor).coerceAtLeast(BigDecimal.ZERO).defaultScale()

        val exits = context.candidates.filter { it.side == OrderSide.EXIT }
        val initial = FundingState(
            sold = exits.associate { it.ticker to it.quantity },
            sells = emptyList(),
            remaining = (buyAmount - deployableCash - exits.sumOf { it.amount ?: BigDecimal.ZERO })
                .coerceAtLeast(BigDecimal.ZERO),
        )
        val funded = sources(context).fold(initial) { state, pool -> state.take(pool) }

        return FundingOutcome(
            sells = funded.sells,
            summary = FundingSummary(
                buyAmount = buyAmount,
                // The pass's own proceeds; the EXITs are already candidates in the plan.
                sellAmount = funded.sells.sumOf { it.amount ?: BigDecimal.ZERO }.defaultScale(),
                unfundedAmount = funded.remaining.defaultScale(),
                neutroDeficit = neutroDeficit(context),
            ),
        )
    }

    // The cascade's ordered pools. Each carries its own availability; the fold subtracts what an
    // earlier pool already sold from the same ticker, so the tiers cannot double-count units.
    private fun sources(context: FundingContext): List<SellablePool> {
        val slicesByTicker: TickerSlices = context.slices.groupBy { it.ticker }
        return neutroSources(slicesByTicker, context) +
            outsideSources(slicesByTicker, context) +
            surplusSources(slicesByTicker, context)
    }

    private fun neutroSources(
        slicesByTicker: TickerSlices,
        context: FundingContext,
    ): List<SellablePool> = slicesByTicker.mapNotNull { (ticker, tickerSlices) ->
        poolOrNull(
            ticker = ticker,
            quantity = tickerSlices.filter { it.neutro }.sumOf { it.attributedQuantity },
            context = context,
            reason = FundingSource.NEUTRO,
            tickerSlices = tickerSlices,
            position = context.positions[ticker] ?: BigDecimal.ZERO,
        )
    }.sortedBy { it.ticker }

    private fun outsideSources(
        slicesByTicker: TickerSlices,
        context: FundingContext,
    ): List<SellablePool> = context.positions.entries
        .filter { (ticker, position) ->
            position.signum() > 0 && slicesByTicker[ticker].orEmpty().none { it.target.signum() > 0 }
        }
        .mapNotNull { (ticker, position) ->
            poolOrNull(
                ticker = ticker,
                quantity = position,
                context = context,
                reason = FundingSource.OUTSIDE_STRATEGY,
                tickerSlices = emptyList(),
                position = position,
            )
        }
        .sortedBy { it.ticker }

    private fun surplusSources(
        slicesByTicker: TickerSlices,
        context: FundingContext,
    ): List<SellablePool> = context.positions.entries
        .filter { (ticker, _) -> slicesByTicker[ticker].orEmpty().isNotEmpty() }
        .mapNotNull { (ticker, position) ->
            val tickerSlices = slicesByTicker[ticker].orEmpty()
            poolOrNull(
                ticker = ticker,
                quantity = position - tickerSlices.targetUnits,
                context = context,
                reason = FundingSource.SURPLUS,
                tickerSlices = tickerSlices,
                position = position,
            )
        }
        .sortedWith(compareByDescending<SellablePool> { it.quantity.multiply(it.price) }.thenBy { it.ticker })

    // A pool is sellable only when it has units and a quote to price them; an unpriced position has no
    // R$ amount to fund a buy with.
    private fun poolOrNull(
        ticker: String,
        quantity: BigDecimal,
        context: FundingContext,
        reason: FundingSource,
        tickerSlices: List<TickerOrderPlan>,
        position: BigDecimal,
    ): SellablePool? {
        if (quantity.signum() <= 0) return null
        val price = priceOf(ticker, context.prices) ?: return null
        return SellablePool(
            ticker = ticker,
            quantity = quantity,
            price = price,
            reason = reason,
            ideal = tickerSlices.sumOf { it.ideal },
            targetQuantity = tickerSlices.targetUnits,
            position = position,
        )
    }

    // The NEUTRO deficit is Ideal minus the attributed units the slices already hold. The ledger can
    // outdate a trade-down, so the held quantity is capped by the ticker's custody — the same cap the
    // sell path uses. An unpriced ticker has no measurable deficit in R$ and is skipped.
    private fun neutroDeficit(context: FundingContext): BigDecimal = context.slices
        .filter { it.neutro }
        .groupBy { it.ticker }
        .entries
        .sumOf { (ticker, tickerSlices) ->
            val price = tickerSlices.first().price?.takeIf { it.signum() > 0 } ?: return@sumOf BigDecimal.ZERO
            val ideal = tickerSlices.sumOf { it.ideal }
            val held = tickerSlices.sumOf { it.attributedQuantity }
                .coerceAtMost(context.positions[ticker] ?: BigDecimal.ZERO)
            (ideal - held.multiply(price)).coerceAtLeast(BigDecimal.ZERO)
        }
        .defaultScale()

    private fun priceOf(ticker: String, prices: TickerPrices): BigDecimal? =
        prices[ticker]?.takeIf { it.signum() > 0 }
}

// One sellable pool, before any of it is spent. `position` and the slice sums are the candidate's
// reporting fields, not its sizing.
private data class SellablePool(
    val ticker: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val reason: FundingSource,
    val ideal: BigDecimal,
    val targetQuantity: BigDecimal,
    val position: BigDecimal,
) {
    fun candidate(units: BigDecimal, amount: BigDecimal): OrderCandidate = OrderCandidate.fundingSell(
        ticker = ticker,
        quantity = units,
        price = price,
        ideal = ideal,
        targetQuantity = targetQuantity,
        position = position,
        fundingSource = reason,
    )
}

// The fold's running state: what has been sold per ticker, the sells emitted, and how much of the
// shortfall is still uncovered.
private data class FundingState(
    val sold: Map<String, BigDecimal>,
    val sells: List<OrderCandidate>,
    val remaining: BigDecimal,
) {
    fun take(pool: SellablePool): FundingState {
        if (remaining.signum() <= 0) return this

        val alreadySold = sold[pool.ticker] ?: BigDecimal.ZERO
        // Attribution is a ledger the user can outdate by trading down a position, so a pool's
        // quantity is capped by custody — never sell units the book no longer holds.
        val sellable = pool.quantity.min(pool.position)
        val available = (sellable - alreadySold).coerceAtLeast(BigDecimal.ZERO)
        val units = remaining.divide(pool.price, 0, RoundingMode.FLOOR).min(available.setScale(0, RoundingMode.FLOOR))
        if (units.signum() <= 0) return this

        val amount = units.multiply(pool.price).defaultScale()
        return FundingState(
            sold = sold + (pool.ticker to alreadySold + units),
            sells = sells + pool.candidate(units = units, amount = amount),
            remaining = (remaining - amount).coerceAtLeast(BigDecimal.ZERO),
        )
    }
}
