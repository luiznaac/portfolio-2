package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.tax.incidence.TaxIncidenceCalculator
import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDate

class TaxServiceTest : StringSpec({

    "should return empty list when no calculators are applicable" {
        val calculator1 = mockk<TaxIncidenceCalculator>()
        val calculator2 = mockk<TaxIncidenceCalculator>()
        val contributionDate = LocalDate.parse("2023-01-01")

        every { calculator1.isApplicable(contributionDate) } returns false
        every { calculator2.isApplicable(contributionDate) } returns false

        val taxService = TaxService(setOf(calculator1, calculator2))
        val result = taxService.getTaxIncidencesBy(contributionDate)

        result.shouldBeEmpty()
        verify(exactly = 1) { calculator1.isApplicable(contributionDate) }
        verify(exactly = 1) { calculator2.isApplicable(contributionDate) }
    }

    "should return tax incidences from applicable calculators only" {
        val calculator1 = mockk<TaxIncidenceCalculator>()
        val calculator2 = mockk<TaxIncidenceCalculator>()
        val calculator3 = mockk<TaxIncidenceCalculator>()
        val contributionDate = LocalDate.parse("2023-01-01")

        val expectedTax1 = TaxIncidence.IOF(96.0)
        val expectedTax3 = TaxIncidence.Renda(22.5)

        every { calculator1.isApplicable(contributionDate) } returns true
        every { calculator2.isApplicable(contributionDate) } returns false
        every { calculator3.isApplicable(contributionDate) } returns true

        every { calculator1.resolve(contributionDate) } returns expectedTax1
        every { calculator3.resolve(contributionDate) } returns expectedTax3

        val taxService = TaxService(setOf(calculator1, calculator2, calculator3))
        val result = taxService.getTaxIncidencesBy(contributionDate)

        result shouldHaveSize 2
        result shouldContainExactlyInAnyOrder listOf(expectedTax1, expectedTax3)

        verify(exactly = 1) { calculator1.isApplicable(contributionDate) }
        verify(exactly = 1) { calculator2.isApplicable(contributionDate) }
        verify(exactly = 1) { calculator3.isApplicable(contributionDate) }
        verify(exactly = 1) { calculator1.resolve(contributionDate) }
        verify(exactly = 1) { calculator3.resolve(contributionDate) }
        verify(exactly = 0) { calculator2.resolve(any()) }
    }
})
