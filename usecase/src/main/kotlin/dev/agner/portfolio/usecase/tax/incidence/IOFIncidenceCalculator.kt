package dev.agner.portfolio.usecase.tax.incidence

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.springframework.stereotype.Component

@Component
class IOFIncidenceCalculator : TaxIncidenceCalculator {

    override fun isApplicable(consolidatingDate: LocalDate, contributionDate: LocalDate) =
        contributionDate.daysUntil(consolidatingDate) in 1..29

    override fun resolve(consolidatingDate: LocalDate, contributionDate: LocalDate): TaxIncidence {
        val daysOfApplication = contributionDate.daysUntil(consolidatingDate)
        val iofRate = calculateIOFRate(daysOfApplication)
        return TaxIncidence.IOF(iofRate)
    }

    private fun calculateIOFRate(daysOfApplication: Int) =
        when (daysOfApplication) {
            1 -> 96.0
            2 -> 93.0
            3 -> 90.0
            4 -> 86.0
            5 -> 83.0
            6 -> 80.0
            7 -> 76.0
            8 -> 73.0
            9 -> 70.0
            10 -> 66.0
            11 -> 63.0
            12 -> 60.0
            13 -> 56.0
            14 -> 53.0
            15 -> 50.0
            16 -> 46.0
            17 -> 43.0
            18 -> 40.0
            19 -> 36.0
            20 -> 33.0
            21 -> 30.0
            22 -> 26.0
            23 -> 23.0
            24 -> 20.0
            25 -> 16.0
            26 -> 13.0
            27 -> 10.0
            28 -> 6.0
            29 -> 3.0
            else -> throw IllegalArgumentException("Invalid days of application for IOF: $daysOfApplication")
        }
}
