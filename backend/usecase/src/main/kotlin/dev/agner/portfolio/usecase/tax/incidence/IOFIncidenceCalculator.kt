package dev.agner.portfolio.usecase.tax.incidence

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class IOFIncidenceCalculator : TaxIncidenceCalculator {

    override fun isApplicable(consolidatingDate: LocalDate, contributionDate: LocalDate) =
        contributionDate.daysUntil(consolidatingDate) + 1 in MIN_IOF_DAY..MAX_IOF_DAY

    override fun resolve(consolidatingDate: LocalDate, contributionDate: LocalDate): TaxIncidence {
        val daysOfApplication = contributionDate.daysUntil(consolidatingDate)
        val iofRate = calculateIOFRate(daysOfApplication + 1)
        return TaxIncidence.IOF(iofRate)
    }

    private fun calculateIOFRate(daysOfApplication: Int): BigDecimal =
        IOF_RATES.getOrNull(daysOfApplication - 1)
            ?: throw IllegalArgumentException("Invalid days of application for IOF: $daysOfApplication")

    private companion object {
        const val MIN_IOF_DAY = 1
        const val MAX_IOF_DAY = 29

        /**
         * The Brazilian IOF table, front-loaded and decaying: entry N-1 is the rate charged on the
         * Nth day of application. Day 30 onwards has no incidence.
         */
        val IOF_RATES = listOf(
            BigDecimal("96.00"),
            BigDecimal("93.00"),
            BigDecimal("90.00"),
            BigDecimal("86.00"),
            BigDecimal("83.00"),
            BigDecimal("80.00"),
            BigDecimal("76.00"),
            BigDecimal("73.00"),
            BigDecimal("70.00"),
            BigDecimal("66.00"),
            BigDecimal("63.00"),
            BigDecimal("60.00"),
            BigDecimal("56.00"),
            BigDecimal("53.00"),
            BigDecimal("50.00"),
            BigDecimal("46.00"),
            BigDecimal("43.00"),
            BigDecimal("40.00"),
            BigDecimal("36.00"),
            BigDecimal("33.00"),
            BigDecimal("30.00"),
            BigDecimal("26.00"),
            BigDecimal("23.00"),
            BigDecimal("20.00"),
            BigDecimal("16.00"),
            BigDecimal("13.00"),
            BigDecimal("10.00"),
            BigDecimal("6.00"),
            BigDecimal("3.00"),
        )
    }
}
