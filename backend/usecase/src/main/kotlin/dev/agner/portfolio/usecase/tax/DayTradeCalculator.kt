package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.isImported
import dev.agner.portfolio.usecase.tax.model.DayTradeWarning
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import java.math.BigDecimal

// One matcher behind both the meter's swing/day-trade split and the report's warning list: a day
// trade is the same ticker traded both ways at one institution on one date. The matched quantity is
// what a same-day buy covers; the rest of the sale stays swing. The warning itself is import-only —
// a manual same-day pair is still a day trade for the arithmetic, but the guard exists to catch what
// a B3 export brought in.
internal object DayTradeCalculator {

    fun matches(trades: List<Trade>): List<DayTradeMatch> {
        val openQuantity = trades
            .filterIsInstance<Trade.Buy>()
            .groupBy { DayTradeKey(it.date, it.institutionId) }
            .mapValues { (_, buys) -> buys.sumOf { it.quantity } }
            .toMutableMap()

        return trades
            .filterIsInstance<Trade.Sell>()
            .sortedWith(compareBy({ it.date }, { it.ordinal }, { it.id }))
            .mapNotNull { sale ->
                val key = DayTradeKey(sale.date, sale.institutionId)
                val open = openQuantity[key] ?: BigDecimal.ZERO
                val matched = minOf(open, sale.quantity)
                openQuantity[key] = open.subtract(matched)
                if (matched.signum() > 0) {
                    DayTradeMatch(
                        sellId = sale.id,
                        date = sale.date,
                        institutionId = sale.institutionId,
                        quantity = matched,
                    )
                } else {
                    null
                }
            }
    }

    fun quantitiesBySale(trades: List<Trade>): Map<Int, BigDecimal> =
        matches(trades).groupBy { it.sellId }.mapValues { (_, ofSale) -> ofSale.sumOf { it.quantity } }

    fun warnings(ticker: String, trades: List<Trade>, period: YearMonth): List<DayTradeWarning> =
        matches(trades.filter { it.isImported })
            .filter { it.date.yearMonth == period }
            .groupBy { DayTradeKey(it.date, it.institutionId) }
            .map { (key, matches) ->
                DayTradeWarning(
                    ticker = ticker,
                    institutionId = key.institutionId,
                    date = key.date,
                    quantity = matches.sumOf { it.quantity }.defaultScale(),
                )
            }
}

internal data class DayTradeMatch(
    val sellId: Int,
    val date: LocalDate,
    val institutionId: Int,
    val quantity: BigDecimal,
)

private data class DayTradeKey(
    val date: LocalDate,
    val institutionId: Int,
)
