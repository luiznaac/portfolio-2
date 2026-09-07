package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.iofIncidence
import dev.agner.portfolio.usecase.rendaIncidence
import dev.agner.portfolio.usecase.tax.incidence.TaxIncidenceCalculator
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDate

class TaxServiceTest : StringSpec({

    "should return empty list when no calculators are applicable" {
        val calculator1 = mockk<TaxIncidenceCalculator>()
        val calculator2 = mockk<TaxIncidenceCalculator>()
        val consolidatingDate = LocalDate.parse("2022-12-15")
        val contributionDate = LocalDate.parse("2023-01-01")

        every { calculator1.isApplicable(consolidatingDate, contributionDate) } returns false
        every { calculator2.isApplicable(consolidatingDate, contributionDate) } returns false

        val taxService = TaxService(setOf(calculator1, calculator2))
        val result = taxService.getTaxIncidencesBy(consolidatingDate, contributionDate)

        result.shouldBeEmpty()
        verify(exactly = 1) { calculator1.isApplicable(consolidatingDate, contributionDate) }
        verify(exactly = 1) { calculator2.isApplicable(consolidatingDate, contributionDate) }
    }

    "should return tax incidences from applicable calculators only" {
        val calculator1 = mockk<TaxIncidenceCalculator>()
        val calculator2 = mockk<TaxIncidenceCalculator>()
        val calculator3 = mockk<TaxIncidenceCalculator>()
        val consolidatingDate = LocalDate.parse("2022-12-15")
        val contributionDate = LocalDate.parse("2023-01-01")

        val expectedTax1 = iofIncidence()
        val expectedTax3 = rendaIncidence()

        every { calculator1.isApplicable(consolidatingDate, contributionDate) } returns true
        every { calculator2.isApplicable(consolidatingDate, contributionDate) } returns false
        every { calculator3.isApplicable(consolidatingDate, contributionDate) } returns true

        every { calculator1.resolve(consolidatingDate, contributionDate) } returns expectedTax1
        every { calculator3.resolve(consolidatingDate, contributionDate) } returns expectedTax3

        val taxService = TaxService(setOf(calculator1, calculator2, calculator3))
        val result = taxService.getTaxIncidencesBy(consolidatingDate, contributionDate)

        result shouldHaveSize 2
        result shouldContainOnly setOf(expectedTax1, expectedTax3)

        verify(exactly = 1) { calculator1.isApplicable(consolidatingDate, contributionDate) }
        verify(exactly = 1) { calculator2.isApplicable(consolidatingDate, contributionDate) }
        verify(exactly = 1) { calculator3.isApplicable(consolidatingDate, contributionDate) }
        verify(exactly = 1) { calculator1.resolve(consolidatingDate, contributionDate) }
        verify(exactly = 1) { calculator3.resolve(consolidatingDate, contributionDate) }
        verify(exactly = 0) { calculator2.resolve(any(), any()) }
    }
})
