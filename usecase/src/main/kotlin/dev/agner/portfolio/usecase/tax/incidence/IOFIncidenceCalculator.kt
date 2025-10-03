package dev.agner.portfolio.usecase.tax.incidence

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.springframework.stereotype.Component

@Component
class IOFIncidenceCalculator : TaxIncidenceCalculator {

    override fun isApplicable(consolidatingDate: LocalDate, contributionDate: LocalDate) =
        contributionDate.daysUntil(consolidatingDate) + 1 in 1..29

    override fun resolve(consolidatingDate: LocalDate, contributionDate: LocalDate): TaxIncidence {
        val daysOfApplication = contributionDate.daysUntil(consolidatingDate)
        val iofRate = calculateIOFRate(daysOfApplication + 1)
        return TaxIncidence.IOF(iofRate.toBigDecimal())
    }

    private fun calculateIOFRate(daysOfApplication: Int) =
        when (daysOfApplication) {
            1 -> "96.00"
            2 -> "93.00"
            3 -> "90.00"
            4 -> "86.00"
            5 -> "83.00"
            6 -> "80.00"
            7 -> "76.00"
            8 -> "73.00"
            9 -> "70.00"
            10 -> "66.00"
            11 -> "63.00"
            12 -> "60.00"
            13 -> "56.00"
            14 -> "53.00"
            15 -> "50.00"
            16 -> "46.00"
            17 -> "43.00"
            18 -> "40.00"
            19 -> "36.00"
            20 -> "33.00"
            21 -> "30.00"
            22 -> "26.00"
            23 -> "23.00"
            24 -> "20.00"
            25 -> "16.00"
            26 -> "13.00"
            27 -> "10.00"
            28 -> "6.00"
            29 -> "3.00"
            else -> throw IllegalArgumentException("Invalid days of application for IOF: $daysOfApplication")
        }
}
