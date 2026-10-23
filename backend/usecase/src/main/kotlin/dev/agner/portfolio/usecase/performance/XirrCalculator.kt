package dev.agner.portfolio.usecase.performance

import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// One dated cash flow for the XIRR: contributions are negative (money leaving the investor's
// pocket), the closing value is positive. Dates need not be evenly spaced.
data class XirrFlow(val date: LocalDate, val amount: BigDecimal)

// The annualized money-weighted return since inception.
//
// The equation is solved on a *daily* rate, which keeps every exponent an integer: a flow `days`
// after inception is discounted by `(1 + d)^days`, and the annualized rate is `(1 + d)^365 - 1`.
// That is the same rate the usual `years = days/365` form defines, without taking a fractional power
// — so the whole computation stays in BigDecimal (backend/AGENTS.md: rates are BigDecimal, always).
object XirrCalculator {

    private const val DAYS_PER_YEAR = 365
    private val MC = MathContext(34, RoundingMode.HALF_EVEN)
    private val ONE = BigDecimal.ONE
    private val ZERO = BigDecimal.ZERO

    // A daily rate below -50% has no economic meaning; above it the search expands its upper bound.
    private val LOW = BigDecimal("-0.5")
    private val TOLERANCE = BigDecimal("1e-15")

    fun calculate(flows: List<XirrFlow>): BigDecimal? {
        if (flows.size < 2) return null
        if (flows.none { it.amount.signum() > 0 } || flows.none { it.amount.signum() < 0 }) return null

        val start = flows.minOf { it.date }
        val dated = flows.map { flow ->
            DatedAmount(
                days = (flow.date.toEpochDays() - start.toEpochDays()).toInt(),
                amount = flow.amount,
            )
        }

        val high = bracket(dated) ?: return null
        val daily = bisect(dated, high)
        val annual = (ONE + daily).pow(DAYS_PER_YEAR, MC).subtract(ONE)

        return annual.multiply(HUNDRED).setScale(PERFORMANCE_SCALE, RoundingMode.HALF_UP)
    }

    // Expands the upper bound until the NPV changes sign, so a very large return still brackets.
    private fun bracket(dated: List<DatedAmount>): BigDecimal? {
        var high = ONE
        repeat(60) {
            if (npv(dated, LOW).multiply(npv(dated, high)).signum() <= 0) return high
            high = high.multiply(BigDecimal(2))
        }
        return null
    }

    private fun bisect(dated: List<DatedAmount>, high: BigDecimal): BigDecimal {
        var left = LOW
        var right = high
        var iterations = 0

        while (iterations < 200 && right.subtract(left).abs() > TOLERANCE) {
            val middle = left.add(right).divide(BigDecimal(2), MC)
            val value = npv(dated, middle)
            if (value.signum() == 0) return middle
            if (npv(dated, left).multiply(value).signum() <= 0) {
                right = middle
            } else {
                left = middle
            }
            iterations++
        }

        return left.add(right).divide(BigDecimal(2), MC)
    }

    private fun npv(dated: List<DatedAmount>, daily: BigDecimal): BigDecimal =
        dated.fold(ZERO) { acc, flow ->
            val discount = (ONE + daily).pow(flow.days, MC)
            acc.add(flow.amount.divide(discount, MC))
        }
}

private data class DatedAmount(val days: Int, val amount: BigDecimal)
