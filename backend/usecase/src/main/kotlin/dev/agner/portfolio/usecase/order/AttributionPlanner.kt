package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.order.model.AttributionMove
import dev.agner.portfolio.usecase.order.model.AttributionSide
import dev.agner.portfolio.usecase.order.model.AttributionTransfer
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.TickerOrderPlan
import java.math.BigDecimal
import java.math.RoundingMode

// The attribution planner's outcome: the per-slice moves an order implies, and the transfers that
// finish aligning the slices with their whole-unit Ideals.
data class AttributionOutcome(
    val moves: List<AttributionMove>,
    val transfers: List<AttributionTransfer>,
)

// One ticker's share of the attribution pass — its slices' moves and transfers.
private data class TickerAttribution(
    val moves: List<AttributionMove>,
    val transfers: List<AttributionTransfer>,
)

// A ticker's projected attribution per Strategy name.
private typealias StrategyQuantities = Map<String, BigDecimal>

// The engine's Attribution bias (D5): one custody Position per ticker, its slices are the stored
// Attribution ledger. A BUY is assigned to the most-below-ideal slice first, a SELL taken from the
// most-above-ideal slice first, and whenever two slices of a ticker still sit ≥ 1 whole unit off
// their Ideal the planner suggests an Attribution Transfer between them. Transfers are bookkeeping
// only: no tax effect, no custody movement. An unpriced ticker cannot be measured in units, so it is
// left alone.
object AttributionPlanner {

    private val ONE = BigDecimal.ONE

    fun compute(slices: List<TickerOrderPlan>, candidates: List<OrderCandidate>): AttributionOutcome {
        val slicesByTicker = slices.groupBy { it.ticker }
        val candidatesByTicker = candidates.groupBy { it.ticker }
        val tickers = (slicesByTicker.keys + candidatesByTicker.keys).sorted()

        val plans = tickers.mapNotNull { ticker ->
            tickerPlan(
                ticker = ticker,
                slices = slicesByTicker[ticker].orEmpty(),
                candidates = candidatesByTicker[ticker].orEmpty(),
            )
        }

        return AttributionOutcome(
            moves = plans.flatMap { it.moves },
            transfers = plans.flatMap { it.transfers },
        )
    }

    // A ticker with no slices is outside every strategy (nothing to attribute); a ticker with an
    // unpriced slice has no whole-unit Ideal to align against.
    private fun tickerPlan(
        ticker: String,
        slices: List<TickerOrderPlan>,
        candidates: List<OrderCandidate>,
    ): TickerAttribution? {
        if (slices.isEmpty() || slices.any { it.targetQuantity == null }) return null

        val initial = slices.associate { it.strategy to it.attributedQuantity }
        val applied = candidates.fold(AttributionState(ticker = ticker, projected = initial, moves = emptyList())) {
                state,
                candidate,
            ->
            state.apply(candidate = candidate, slices = slices)
        }

        return TickerAttribution(
            moves = applied.moves,
            transfers = transfers(ticker = ticker, slices = slices, projected = applied.projected),
        )
    }

    // The running attribution after the orders so far: each slice's projected quantity and the moves
    // that got it there.
    private data class AttributionState(
        val ticker: String,
        val projected: StrategyQuantities,
        val moves: List<AttributionMove>,
    ) {
        fun apply(candidate: OrderCandidate, slices: List<TickerOrderPlan>): AttributionState =
            if (candidate.side.isBuy) {
                allocate(quantity = candidate.quantity, slices = slices)
            } else {
                deallocate(quantity = candidate.quantity, slices = slices)
            }

        // Most-below-ideal first: the slice with the largest unit shortfall takes units until it
        // reaches its Target. A leftover no slice needs (an attribution ledger shorter than the
        // custody) lands on the most-below slice, which the transfer pass then corrects.
        private fun allocate(quantity: BigDecimal, slices: List<TickerOrderPlan>): AttributionState {
            val ranked = slices.filterNot { it.neutro }
                .sortedWith(compareByDescending<TickerOrderPlan> { it.shortfall(projected) }.thenBy { it.strategy })
            val allocated = ranked.fold(
                allocations(side = AttributionSide.ALLOCATE, quantity = quantity),
            ) { state, slice ->
                state.take(quantity = slice.shortfall(projected), strategy = slice.strategy)
            }
            val withLeftover = ranked.firstOrNull()
                ?.let { allocated.take(quantity = allocated.remaining, strategy = it.strategy) }
                ?: allocated
            return AttributionState(ticker = ticker, projected = withLeftover.projected, moves = withLeftover.moves)
        }

        // Most-above-ideal first: take the sold units back from the slices that hold the most over
        // their Target, then continue down the same order through the slices already below, and only
        // then dip into the above-ideal slices' remaining units. Every pass respects what the slice
        // still holds, so a sale never drives attribution negative.
        private fun deallocate(quantity: BigDecimal, slices: List<TickerOrderPlan>): AttributionState {
            val ranked = slices.sortedWith(
                compareByDescending<TickerOrderPlan> { it.surplus(projected) }.thenBy { it.strategy },
            )
            val surplusPass = ranked.fold(allocations(side = AttributionSide.DEALLOCATE, quantity = quantity)) {
                    state,
                    slice,
                ->
                state.take(
                    quantity = slice.surplus(projected).coerceAtLeast(BigDecimal.ZERO),
                    strategy = slice.strategy,
                )
            }
            val heldPass = ranked
                .filter { it.surplus(projected).signum() <= 0 }
                .fold(surplusPass) { state, slice ->
                    val held = state.projected[slice.strategy] ?: BigDecimal.ZERO
                    state.take(quantity = slice.shortfall(projected).min(held), strategy = slice.strategy)
                }
            val drained = ranked
                .filter { it.surplus(projected).signum() > 0 }
                .fold(heldPass) { state, slice ->
                    state.take(quantity = state.projected[slice.strategy] ?: BigDecimal.ZERO, strategy = slice.strategy)
                }
            return AttributionState(ticker = ticker, projected = drained.projected, moves = drained.moves)
        }

        private fun allocations(side: AttributionSide, quantity: BigDecimal) = AllocationState(
            ticker = ticker,
            side = side,
            projected = projected,
            remaining = quantity,
            moves = moves,
        )
    }

    private data class AllocationState(
        val ticker: String,
        val side: AttributionSide,
        val projected: StrategyQuantities,
        val remaining: BigDecimal,
        val moves: List<AttributionMove>,
    ) {
        fun take(quantity: BigDecimal, strategy: String): AllocationState {
            val taken = remaining.min(quantity)
            if (taken.signum() <= 0) return this
            val delta = if (side == AttributionSide.ALLOCATE) taken else taken.negate()
            return AllocationState(
                ticker = ticker,
                side = side,
                projected = projected + (strategy to (projected[strategy] ?: BigDecimal.ZERO) + delta),
                remaining = remaining - taken,
                moves = moves + AttributionMove(
                    ticker = ticker,
                    strategy = strategy,
                    side = side,
                    quantity = taken,
                ),
            )
        }
    }

    // The transfer pass: pair every slice above its whole-unit Target with one below, largest-surplus
    // donors to largest-deficit receivers, and move only whole units (≥ 1, so a sub-unit drift is not
    // a transfer).
    private fun transfers(
        ticker: String,
        slices: List<TickerOrderPlan>,
        projected: StrategyQuantities,
    ): List<AttributionTransfer> {
        val price = slices.first().price?.takeIf { it.signum() > 0 }
        val donors = slices
            .map { Donor(strategy = it.strategy, available = it.surplus(projected).setScale(0, RoundingMode.FLOOR)) }
            .filter { it.available >= ONE }
            .sortedWith(compareByDescending<Donor> { it.available }.thenBy { it.strategy })
        val receivers = slices
            .map { Receiver(strategy = it.strategy, deficit = it.shortfall(projected).setScale(0, RoundingMode.FLOOR)) }
            .filter { it.deficit >= ONE }
            .sortedWith(compareByDescending<Receiver> { it.deficit }.thenBy { it.strategy })

        val state = receivers.fold(TransferState(donors = donors, transfers = emptyList())) { acc, receiver ->
            acc.serve(receiver = receiver.strategy, deficit = receiver.deficit, ticker = ticker, price = price)
        }
        return state.transfers
    }

    private data class Donor(val strategy: String, val available: BigDecimal)

    private data class Receiver(val strategy: String, val deficit: BigDecimal)

    private data class TransferState(
        val donors: List<Donor>,
        val transfers: List<AttributionTransfer>,
    ) {
        fun serve(receiver: String, deficit: BigDecimal, ticker: String, price: BigDecimal?): TransferState {
            val consumed = donors.fold(ServedState(remaining = deficit, donors = emptyList(), transfers = transfers)) {
                    state,
                    donor,
                ->
                if (state.remaining.signum() <= 0 || donor.available.signum() <= 0) {
                    state.copy(donors = state.donors + donor)
                } else {
                    val quantity = state.remaining.min(donor.available)
                    state.copy(
                        remaining = state.remaining - quantity,
                        donors = state.donors + donor.copy(available = donor.available - quantity),
                        transfers = state.transfers + AttributionTransfer(
                            ticker = ticker,
                            fromStrategy = donor.strategy,
                            toStrategy = receiver,
                            quantity = quantity,
                            price = price,
                            amount = price?.multiply(quantity)?.defaultScale(),
                        ),
                    )
                }
            }
            return TransferState(donors = consumed.donors, transfers = consumed.transfers)
        }
    }

    private data class ServedState(
        val remaining: BigDecimal,
        val donors: List<Donor>,
        val transfers: List<AttributionTransfer>,
    )

    // Units the slice sits below its whole-unit Target (positive = below Ideal).
    private fun TickerOrderPlan.shortfall(projected: StrategyQuantities): BigDecimal =
        (targetQuantity ?: BigDecimal.ZERO) - (projected[strategy] ?: BigDecimal.ZERO)

    // Units the slice sits above its Target (the negative of the shortfall).
    private fun TickerOrderPlan.surplus(projected: StrategyQuantities): BigDecimal =
        -shortfall(projected)
}
