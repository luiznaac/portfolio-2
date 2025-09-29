package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.extension.mapToSet
import dev.agner.portfolio.usecase.tax.incidence.TaxIncidenceCalculator
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

@Service
class TaxService(
    private val calculators: Set<TaxIncidenceCalculator>,
) {

    fun getTaxIncidencesBy(contributionDate: LocalDate) =
        calculators
            .filter { it.isApplicable(contributionDate) }
            .mapToSet { it.resolve(contributionDate) }
}
