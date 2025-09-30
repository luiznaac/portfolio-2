package dev.agner.portfolio.usecase.tax.incidence

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import kotlinx.datetime.LocalDate

interface TaxIncidenceCalculator {
    fun isApplicable(consolidatingDate: LocalDate, contributionDate: LocalDate): Boolean

    fun resolve(consolidatingDate: LocalDate, contributionDate: LocalDate): TaxIncidence
}
