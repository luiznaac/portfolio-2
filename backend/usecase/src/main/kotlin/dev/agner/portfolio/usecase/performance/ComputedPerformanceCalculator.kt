package dev.agner.portfolio.usecase.performance

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.income.model.Income
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.performance.model.PeriodClass
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import java.math.BigDecimal

// The rules of the pre-planning era: the month window the ledger alone supports, the aportes a month
// derives from its trades and income, and the month-end value of the as-of Position replay. Nothing
// here writes a Month, a close or a Snapshot — the era is a read over the live ledger.
object ComputedPerformanceCalculator {

    // Every month from the earliest ledger row to the month before the first close (or the last
    // completed calendar month while no month is closed). The current, incomplete month is never a
    // point.
    fun window(
        earliestLedgerMonth: YearMonth?,
        firstSnapshotMonth: YearMonth?,
        lastCompletedMonth: YearMonth,
    ): List<YearMonth> {
        val start = earliestLedgerMonth ?: return emptyList()
        val end = firstSnapshotMonth?.previousMonth() ?: lastCompletedMonth
        if (start > end) return emptyList()

        return generateSequence(start) { it.nextMonth() }
            .takeWhile { it <= end }
            .toList()
    }

    // A month's external money: buys cost money, sells return it and fees sit on the side that pays.
    // Income already arrived inside the month's value but is not a contribution, so it is netted out
    // of the flow. An Opening Trade is the adoption seed, not money that entered during the era.
    fun netAportes(
        trades: List<Trade>,
        incomes: List<Income>,
        period: YearMonth,
    ): BigDecimal {
        val inMonth = trades.filter { it.date.yearMonth == period && it.source != TradeSource.OPENING }
        val buyCost = inMonth.filterIsInstance<Trade.Buy>()
            .sumOf { it.quantity.multiply(it.unitPrice).add(it.fee ?: BigDecimal.ZERO) }
        val sellProceeds = inMonth.filterIsInstance<Trade.Sell>()
            .sumOf { it.quantity.multiply(it.unitPrice).subtract(it.fee ?: BigDecimal.ZERO) }

        return buyCost.subtract(sellProceeds).subtract(netIncome(incomes, period)).defaultScale()
    }

    // The month's net income: it arrived inside the month's value but is not a contribution.
    fun netIncome(incomes: List<Income>, period: YearMonth): BigDecimal =
        incomes.filter { it.date.yearMonth == period }
            .sumOf { it.netAmount }
            .defaultScale()

    // The priced month-end value: every held ticker's units times its last close on or before the
    // month end. A ticker with no close at all is unknown — excluded from the total and counted as a
    // partial month — while a delisted ticker keeps the close it still has.
    fun valueAt(
        positions: List<Position>,
        closes: Map<String, BigDecimal>,
        assetClasses: Map<String, AssetClass?>,
    ): ComputedMonthValue {
        val held = positions.filter { it.quantity.signum() != 0 }
        val partial = held.any { closes[it.ticker] == null }
        val priced = held.mapNotNull { position ->
            val close = closes[position.ticker] ?: return@mapNotNull null
            PricedPosition(
                assetClass = assetClasses[position.ticker],
                value = position.quantity.multiply(close).defaultScale(),
            )
        }

        val classes = priced
            .filter { it.assetClass != null }
            .groupBy({ it.assetClass!! }, { it.value })
            .map { (assetClass, values) ->
                PeriodClass(assetClass, values.fold(BigDecimal.ZERO, BigDecimal::add).defaultScale())
            }
            .sortedBy { it.assetClass.ordinal }

        return ComputedMonthValue(
            value = priced.fold(BigDecimal.ZERO) { total, pricedPosition -> total + pricedPosition.value }
                .defaultScale(),
            partial = partial,
            classes = classes,
        )
    }

    private fun YearMonth.previousMonth(): YearMonth =
        LocalDate(year, month, 1).minus(1, DateTimeUnit.MONTH).yearMonth

    private fun YearMonth.nextMonth(): YearMonth =
        LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).yearMonth

    private data class PricedPosition(val assetClass: AssetClass?, val value: BigDecimal)
}

// The month-end Portfolio value and its per-class split, with the flag that says a held ticker could
// not be priced and was left out.
data class ComputedMonthValue(
    val value: BigDecimal,
    val partial: Boolean,
    val classes: List<PeriodClass>,
)
