package dev.agner.portfolio.usecase.tax.incidence

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.springframework.stereotype.Component

@Component
class RendaIncidenceCalculator : TaxIncidenceCalculator {

    override fun isApplicable(consolidatingDate: LocalDate, contributionDate: LocalDate) = true

    override fun resolve(consolidatingDate: LocalDate, contributionDate: LocalDate): TaxIncidence {
        val daysOfApplication = contributionDate.daysUntil(consolidatingDate)
        val rendaRate = calculateRendaRate(daysOfApplication)
        return TaxIncidence.Renda(rendaRate.toBigDecimal())
    }

    private fun calculateRendaRate(daysOfApplication: Int) =
        when {
            daysOfApplication <= 180 -> "22.50"
            daysOfApplication <= 360 -> "20.00"
            daysOfApplication <= 720 -> "17.50"
            else -> "15.00"
        }
}
