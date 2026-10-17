package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.Trade
import java.math.BigDecimal
import java.math.RoundingMode

// The ledger-replay arithmetic behind a stored Position. A buy re-weights the average price by the
// units it adds; a sell removes units and leaves the average untouched (the standard Brazilian
// fiscal moving average). When the running quantity reaches zero or below the average resets — a
// fresh position has no cost basis. Pure and side-effect free, so it is unit-tested without a
// repository or a clock.
object PositionCalculator {

    // The stored columns are decimal(18,6); the average is rounded to the same scale so a replayed
    // projection compares equal to a fresh read.
    private const val AVERAGE_SCALE = 6

    // Trades are replayed oldest first. The ledger order is (date, id) — id breaks same-day ties in
    // insertion order so a replay is deterministic.
    fun replay(ticker: String, trades: List<Trade>): Position =
        trades.sortedWith(compareBy({ it.date }, { it.id }))
            .fold(Position.empty(ticker)) { position, trade -> apply(position, trade) }

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
}
