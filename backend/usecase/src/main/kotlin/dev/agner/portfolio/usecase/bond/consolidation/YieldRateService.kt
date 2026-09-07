package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.YieldRateContext
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.Bond.FixedRateBond
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.commons.removeWeekends
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.index.IndexValueService
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode.HALF_EVEN
import java.time.Clock
import kotlin.collections.flatMap
import kotlin.math.exp
import kotlin.math.ln

@Service
class YieldRateService(
    private val indexValueService: IndexValueService,
    private val clock: Clock,
) {

    suspend fun buildRateFor(bond: Bond, startingAt: LocalDate) = when (bond) {
        is FloatingRateBond -> bond.buildRates(startingAt)
        is FixedRateBond -> bond.buildRates(startingAt)
    }

    private suspend fun FloatingRateBond.buildRates(startingAt: LocalDate) =
        indexValueService.fetchAllBy(indexId, startingAt)
            .associate { it.date to YieldRateContext(value, it) }

    private suspend fun FixedRateBond.buildRates(startingAt: LocalDate) =
        (startingAt.year..LocalDate.today(clock).year)
            .flatMap { year ->
                val firstDayOfTheYear = YearMonth(year, Month.JANUARY).firstDay
                val lastDayOfTheYear = YearMonth(year, Month.DECEMBER).lastDay
                val allWorkingDays = (firstDayOfTheYear..lastDayOfTheYear).removeWeekends()

                // Calculate daily rate
                val annualRate = value.divide(BigDecimal("100"), 20, HALF_EVEN)
                val onePlusRate = BigDecimal.ONE.add(annualRate) // e.g., 1.15
                val daysCount = BigDecimal(allWorkingDays.size)
                val exponent = BigDecimal.ONE.divide(daysCount, 20, HALF_EVEN)

                // Calculate (1 + rate)^(1/days) using natural logarithm approach
                val lnOnePlusRate = BigDecimal(ln(onePlusRate.toDouble()))
                val lnResult = lnOnePlusRate.multiply(exponent)
                val dailyFactor = BigDecimal(exp(lnResult.toDouble()))

                // Convert back to percentage rate
                val dailyRate = dailyFactor.subtract(BigDecimal.ONE)
                val tx = dailyRate.multiply(BigDecimal("100")).setScale(8, HALF_EVEN)

                allWorkingDays
                    .filter { it >= startingAt }
                    .map { it to YieldRateContext(tx) }
            }
            .toMap()
}
