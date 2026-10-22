package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.corporateaction.CorporateActionCalculator
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.Trade
import java.math.BigDecimal
import java.math.RoundingMode

// The ledger-replay arithmetic behind a stored Position. A buy re-weights the average price by the
// units it adds; a sell removes units and leaves the average untouched (the standard Brazilian
// fiscal moving average). When the running quantity reaches zero or below the average resets — a
// fresh position has no cost basis. Corporate actions join the replay on their ex-date: they are
// the only other way a position's quantity or cost moves, so leaving them out would let the next
// trade insert erase them. Pure and side-effect free, so it is unit-tested without a repository or
// a clock.
object PositionCalculator {

    // The stored columns are decimal(18,6); the average is rounded to the same scale so a replayed
    // projection compares equal to a fresh read.
    private const val AVERAGE_SCALE = 6

    // Trades and actions are replayed oldest first. On one date an action applies before a trade —
    // an ex-date trade is already priced ex — and same-day actions follow their ordinal (then id)
    // so a replay is deterministic. A trade's id breaks same-day ties in insertion order.
    fun replay(
        ticker: String,
        trades: List<Trade>,
        corporateActions: List<CorporateAction> = emptyList(),
    ): Position =
        entries(trades, corporateActions)
            .sortedBy { it.key }
            .fold(Position.empty(ticker)) { position, entry -> entry.apply(position) }

    // The holder's position immediately before one event — the quantity a cash entitlement is paid
    // on. It is the replay up to (exDate, action ordinal, action id): an event on a trade's own date
    // sees the pre-trade position, and a later event of the same day is not folded in yet.
    fun positionBefore(
        ticker: String,
        trades: List<Trade>,
        corporateActions: List<CorporateAction>,
        action: CorporateAction,
    ): Position {
        val boundary = TimelineKey(action.exDate, ACTION_PRIORITY, action.ordinal, action.id)
        return entries(trades, corporateActions)
            .filter { it.key < boundary }
            .sortedBy { it.key }
            .fold(Position.empty(ticker)) { position, entry -> entry.apply(position) }
    }

    private fun entries(trades: List<Trade>, corporateActions: List<CorporateAction>): List<LedgerEntry> =
        trades.map { LedgerEntry.of(it) } + corporateActions.map { LedgerEntry.of(it) }

    private fun apply(position: Position, trade: Trade): Position = when (trade) {
        is Trade.Buy -> buy(position, trade.quantity, trade.unitPrice)
        is Trade.Sell -> sell(position, trade.quantity)
    }

    private fun buy(position: Position, quantity: BigDecimal, unitPrice: BigDecimal): Position {
        val newQuantity = position.quantity + quantity
        val cost = position.quantity * position.averagePrice + quantity * unitPrice
        // A buy that only offsets an earlier over-sell lands the quantity at zero, where a weighted
        // average has no meaning — reset it like the sell path instead of dividing by zero.
        val average = if (newQuantity.signum() > 0) {
            cost.divide(newQuantity, AVERAGE_SCALE, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        return position.copy(quantity = newQuantity, averagePrice = average)
    }

    private fun sell(position: Position, quantity: BigDecimal): Position {
        val newQuantity = position.quantity - quantity
        val average = if (newQuantity.signum() > 0) position.averagePrice else BigDecimal.ZERO
        return position.copy(quantity = newQuantity, averagePrice = average)
    }

    private data class TimelineKey(
        val date: kotlinx.datetime.LocalDate,
        val priority: Int,
        val ordinal: Int,
        val id: Int,
    ) : Comparable<TimelineKey> {
        override fun compareTo(other: TimelineKey): Int =
            compareValuesBy(this, other, { it.date }, { it.priority }, { it.ordinal }, { it.id })
    }

    private sealed class LedgerEntry {
        abstract val key: TimelineKey
        abstract fun apply(position: Position): Position

        data class TradeEntry(val trade: Trade) : LedgerEntry() {
            override val key get() = TimelineKey(trade.date, TRADE_PRIORITY, trade.ordinal, trade.id)
            override fun apply(position: Position) = apply(position, trade)
        }

        data class ActionEntry(val action: CorporateAction) : LedgerEntry() {
            override val key get() = TimelineKey(action.exDate, ACTION_PRIORITY, action.ordinal, action.id)
            override fun apply(position: Position) =
                if (action.isApplied) CorporateActionCalculator.apply(position, action) else position
        }

        companion object {
            fun of(trade: Trade) = TradeEntry(trade)
            fun of(action: CorporateAction) = ActionEntry(action)
        }
    }

    private const val ACTION_PRIORITY = 0
    private const val TRADE_PRIORITY = 1
}
