package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.corporateaction.CorporateActionCalculator
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.Trade
import kotlinx.datetime.LocalDate
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
    // an ex-date trade is already priced ex — a rename applies before any other same-day action (its
    // identity move is what makes a quantity event keyed to either code land on one book), and
    // same-day actions then follow their ordinal (then id) so a replay is deterministic. A trade's
    // id breaks same-day ties in insertion order.
    fun replay(
        ticker: String,
        trades: List<Trade>,
        corporateActions: List<CorporateAction> = emptyList(),
        gaps: List<Gap> = emptyList(),
    ): Position = replayEntries(ticker, entries(trades, corporateActions, gaps))

    // The position at the end of a date — the custody an income entitlement is paid on. It is the
    // replay of every entry dated on or before the day, so a trade and an event of that same day
    // are both folded in.
    fun positionOn(
        ticker: String,
        trades: List<Trade>,
        corporateActions: List<CorporateAction>,
        date: LocalDate,
        gaps: List<Gap> = emptyList(),
    ): Position = replayEntries(
        ticker,
        entries(trades, corporateActions, gaps).filter { it.key.date <= date },
    )

    // The holder's position immediately before one event — the quantity a cash entitlement is paid
    // on. It is the replay up to (exDate, action ordinal, action id): an event on a trade's own date
    // sees the pre-trade position, and a later event of the same day is not folded in yet.
    fun positionBefore(
        ticker: String,
        trades: List<Trade>,
        corporateActions: List<CorporateAction>,
        action: CorporateAction,
        gaps: List<Gap> = emptyList(),
    ): Position = replayBefore(
        ticker,
        entries(trades, corporateActions, gaps),
        TimelineKey.of(action),
    )

    // The position immediately before one trade — the fiscal basis a sale's gain is measured on.
    // Later trades and actions are excluded, while a same-day corporate action still counts: an
    // ex-date trade is already priced ex.
    fun positionBefore(
        ticker: String,
        trades: List<Trade>,
        corporateActions: List<CorporateAction>,
        trade: Trade,
        gaps: List<Gap> = emptyList(),
    ): Position = replayBefore(
        ticker,
        entries(trades, corporateActions, gaps),
        TimelineKey.of(trade),
    )

    private fun replayBefore(ticker: String, entries: List<LedgerEntry>, boundary: TimelineKey): Position =
        replayEntries(ticker, entries.filter { it.key < boundary })

    private fun replayEntries(ticker: String, entries: List<LedgerEntry>): Position =
        entries.sortedBy { it.key }.fold(Position.empty(ticker)) { position, entry -> entry.apply(position) }

    private fun entries(
        trades: List<Trade>,
        corporateActions: List<CorporateAction>,
        gaps: List<Gap>,
    ): List<LedgerEntry> =
        trades.map { LedgerEntry.of(it) } +
            // Only an applied event moves the book, and an applied event always carries its
            // ex-date; a parked row (which may lack one) contributes nothing to the replay.
            corporateActions.filter { it.isApplied && it.exDate != null }.map { LedgerEntry.of(it) } +
            gaps.filter { it.status == GapStatus.OPEN }.map { LedgerEntry.of(it) }

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
        val rank: Int,
        val ordinal: Int,
        val id: Int,
    ) : Comparable<TimelineKey> {
        override fun compareTo(other: TimelineKey): Int =
            compareValuesBy(this, other, { it.date }, { it.priority }, { it.rank }, { it.ordinal }, { it.id })

        // One factory per ledger entry kind: the same-typed ints (priority, rank, ordinal, id) are
        // meaningless when named at a call site, and the boundary key a `positionBefore` builds must
        // match its entry's key exactly.
        companion object {
            fun of(trade: Trade) = TimelineKey(
                date = trade.date,
                priority = TRADE_PRIORITY,
                rank = NO_RANK,
                ordinal = trade.ordinal,
                id = trade.id,
            )

            fun of(action: CorporateAction) = TimelineKey(
                date = requireNotNull(action.exDate) { "an applied action ${action.id} has no ex-date" },
                priority = ACTION_PRIORITY,
                rank = action.kind.replayRank,
                ordinal = action.ordinal,
                id = action.id,
            )

            fun of(gap: Gap) = TimelineKey(
                date = gap.date,
                priority = GAP_PRIORITY,
                rank = NO_RANK,
                ordinal = 0,
                id = gap.id,
            )
        }
    }

    private sealed class LedgerEntry {
        abstract val key: TimelineKey
        abstract fun apply(position: Position): Position

        data class TradeEntry(val trade: Trade) : LedgerEntry() {
            override val key get() = TimelineKey.of(trade)
            override fun apply(position: Position) = apply(position, trade)
        }

        data class ActionEntry(val action: CorporateAction) : LedgerEntry() {
            override val key get() = TimelineKey.of(action)
            override fun apply(position: Position) =
                if (action.isApplied) CorporateActionCalculator.apply(position, action) else position
        }

        data class GapEntry(val gap: Gap) : LedgerEntry() {
            override val key get() = TimelineKey.of(gap)
            override fun apply(position: Position) = when (gap) {
                is Gap.Buy -> buy(position, gap.quantity, gap.unitPrice)
                is Gap.Sell -> sell(position, gap.quantity)
            }
        }

        companion object {
            fun of(trade: Trade) = TradeEntry(trade)
            fun of(action: CorporateAction) = ActionEntry(action)
            fun of(gap: Gap) = GapEntry(gap)
        }
    }

    // Trades and gaps carry no event kind, so they rank below every action's own rank; the priority
    // already separates them from actions, and the constant names that these entries have no kind.
    private const val NO_RANK = 0
    private const val ACTION_PRIORITY = 0
    private const val GAP_PRIORITY = 1
    private const val TRADE_PRIORITY = 2
}
