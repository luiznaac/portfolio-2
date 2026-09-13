package dev.agner.portfolio.usecase.tax.incidence

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class RendaIncidenceCalculator : TaxIncidenceCalculator {

    override fun isApplicable(consolidatingDate: LocalDate, contributionDate: LocalDate) = true

    override fun resolve(consolidatingDate: LocalDate, contributionDate: LocalDate): TaxIncidence {
        val daysOfApplication = contributionDate.daysUntil(consolidatingDate)
        val rendaRate = calculateRendaRate(daysOfApplication)
        return TaxIncidence.Renda(rendaRate)
    }

    private fun calculateRendaRate(daysOfApplication: Int): BigDecimal = when {
        daysOfApplication <= FIRST_BRACKET_END_DAY -> RENDA_RATE_UP_TO_180_DAYS
        daysOfApplication <= SECOND_BRACKET_END_DAY -> RENDA_RATE_UP_TO_360_DAYS
        daysOfApplication <= THIRD_BRACKET_END_DAY -> RENDA_RATE_UP_TO_720_DAYS
        else -> RENDA_RATE_ABOVE_720_DAYS
    }

    private companion object {
        // Renda brackets by holding period: 22.5% up to 180 days, 20% up to 360, 17.5% up to 720,
        // and 15% beyond that.
        const val FIRST_BRACKET_END_DAY = 180
        const val SECOND_BRACKET_END_DAY = 360
        const val THIRD_BRACKET_END_DAY = 720

        val RENDA_RATE_UP_TO_180_DAYS = BigDecimal("22.50")
        val RENDA_RATE_UP_TO_360_DAYS = BigDecimal("20.00")
        val RENDA_RATE_UP_TO_720_DAYS = BigDecimal("17.50")
        val RENDA_RATE_ABOVE_720_DAYS = BigDecimal("15.00")
    }
}
