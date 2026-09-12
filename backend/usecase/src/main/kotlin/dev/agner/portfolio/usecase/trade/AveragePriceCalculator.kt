package dev.agner.portfolio.usecase.trade

import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction.Bonus
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction.ReverseSplit
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction.Split
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction.TickerChange
import dev.agner.portfolio.usecase.trade.model.Position
import dev.agner.portfolio.usecase.trade.model.RealizedGain
import dev.agner.portfolio.usecase.trade.model.Trade
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure replay of trades and corporate actions into a current [Position] and the [RealizedGain]s
 * along the way. Never fed a running balance: a corporate action discovered late is inserted with
 * its real date and everything downstream recalculates, instead of requiring a manual correction.
 *
 * Cost basis uses the weighted-average method, as required by Brazilian tax law (Receita Federal),
 * never FIFO/LIFO.
 */
@Component
class AveragePriceCalculator {

    fun calculate(trades: List<Trade>, corporateActions: List<CorporateAction>): Result {
        val timeline = buildTimeline(trades, corporateActions)

        val final = timeline.fold(Accumulator()) { acc, event ->
            when (event) {
                is Event.TradeEvent -> acc.apply(event.trade)
                is Event.ActionEvent -> acc.apply(event.action)
            }
        }

        return Result(position = final.toPosition(), realizedGains = final.gains)
    }

    private fun buildTimeline(trades: List<Trade>, actions: List<CorporateAction>): List<Event> =
        (trades.map { Event.TradeEvent(it) } + actions.map { Event.ActionEvent(it) })
            // A corporate action effective on the same day as a trade is applied first: a split
            // declared effective on the trade date is already reflected in that day's execution price.
            .sortedWith(compareBy({ it.date() }, { it !is Event.ActionEvent }))

    private sealed class Event {
        data class TradeEvent(val trade: Trade) : Event()
        data class ActionEvent(val action: CorporateAction) : Event()

        fun date(): LocalDate = when (this) {
            is TradeEvent -> trade.date
            is ActionEvent -> action.date
        }
    }

    private data class Accumulator(
        val quantity: BigDecimal = BigDecimal.ZERO,
        val totalCost: BigDecimal = BigDecimal.ZERO,
        val gains: List<RealizedGain> = emptyList(),
    ) {
        val averagePrice: BigDecimal
            get() = if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                totalCost.divide(quantity, 6, RoundingMode.HALF_EVEN)
            }

        fun apply(trade: Trade): Accumulator = when (trade) {
            is Trade.Buy -> copy(
                quantity = quantity + trade.quantity,
                totalCost = totalCost + trade.quantity * trade.price,
            )

            is Trade.Sell -> {
                val costBasis = (trade.quantity * averagePrice).setScale(2, RoundingMode.HALF_EVEN)
                val proceeds = (trade.quantity * trade.price).setScale(2, RoundingMode.HALF_EVEN)

                copy(
                    quantity = quantity - trade.quantity,
                    totalCost = totalCost - costBasis,
                    gains = gains + RealizedGain(
                        tradeId = trade.id,
                        date = trade.date,
                        quantity = trade.quantity,
                        proceeds = proceeds,
                        costBasis = costBasis,
                    ),
                )
            }
        }

        fun apply(action: CorporateAction): Accumulator = when (action) {
            is Split -> copy(quantity = quantity * action.ratio)
            is ReverseSplit -> copy(quantity = quantity.divide(action.ratio, 8, RoundingMode.HALF_EVEN))
            is Bonus -> {
                val newShares = quantity * action.ratio
                copy(
                    quantity = quantity + newShares,
                    totalCost = totalCost + newShares * action.valuePerNewShare,
                )
            }
            // Renaming doesn't touch the numbers; ListedAssetService.changeTicker is what reacts to it.
            is TickerChange -> this
        }

        fun toPosition() = Position(
            quantity = quantity,
            averagePrice = averagePrice,
            totalCost = totalCost.setScale(2, RoundingMode.HALF_EVEN),
        )
    }

    data class Result(
        val position: Position,
        val realizedGains: List<RealizedGain>,
    )
}
