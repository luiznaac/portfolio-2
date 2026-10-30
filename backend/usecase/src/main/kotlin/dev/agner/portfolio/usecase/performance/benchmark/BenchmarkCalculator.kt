package dev.agner.portfolio.usecase.performance.benchmark

import dev.agner.portfolio.usecase.performance.PERFORMANCE_SCALE
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

// Turns a benchmark's stored observations into a period return. Rates compound over the period
// (the daily CDI and the monthly IPCA are both percent variations); an index level is measured
// against its last close before the period. A series without enough coverage has no return at all.
object BenchmarkCalculator {

    fun returnFor(
        index: BenchmarkIndex,
        observations: List<BenchmarkObservation>,
        from: LocalDate,
        to: LocalDate,
    ): BigDecimal? = if (!BenchmarkCoverage.covers(index, observations, from, to)) {
        null
    } else {
        when (index.kind) {
            BenchmarkKind.DAILY_RATE, BenchmarkKind.MONTHLY_VARIATION -> compound(observations, from, to)
            BenchmarkKind.INDEX_LEVEL -> levelReturn(observations, from, to)
        }
    }

    private fun compound(observations: List<BenchmarkObservation>, from: LocalDate, to: LocalDate): BigDecimal? {
        val inPeriod = observations
            .sortedBy { it.date }
            .filter { it.date >= from && it.date <= to }
        if (inPeriod.isEmpty()) return null

        val factor = inPeriod.fold(BigDecimal.ONE) { acc, point ->
            acc.multiply(BigDecimal.ONE + point.value.movePointLeft(2))
        }
        return (factor - BigDecimal.ONE).movePointRight(2).setScale(PERFORMANCE_SCALE, RoundingMode.HALF_UP)
    }

    private fun levelReturn(
        observations: List<BenchmarkObservation>,
        from: LocalDate,
        to: LocalDate,
    ): BigDecimal? {
        val sorted = observations.sortedBy { it.date }
        val base = sorted.lastOrNull { it.date < from }?.value ?: return null
        val end = sorted.lastOrNull { it.date <= to }?.value ?: return null
        if (base.signum() == 0) return null

        return (end - base).movePointRight(2).divide(base, PERFORMANCE_SCALE, RoundingMode.HALF_UP)
    }
}
