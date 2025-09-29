package dev.agner.portfolio.usecase.tax.incidence

import dev.agner.portfolio.usecase.extension.today
import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class RendaIncidenceCalculator(
    private val clock: Clock,
) : TaxIncidenceCalculator {

    override fun isApplicable(contributionDate: LocalDate) = true

    override fun resolve(contributionDate: LocalDate): TaxIncidence {
        val daysOfApplication = contributionDate.daysUntil(LocalDate.today(clock))
        val rendaRate = calculateRendaRate(daysOfApplication)
        return TaxIncidence.Renda(rendaRate)
    }

    private fun calculateRendaRate(daysOfApplication: Int) =
        when {
            daysOfApplication <= 180 -> 22.5
            daysOfApplication <= 360 -> 20.0
            daysOfApplication <= 720 -> 17.5
            else -> 15.0
        }
}
