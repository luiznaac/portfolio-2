package dev.agner.portfolio.usecase.tax.incidence

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.math.BigDecimal

class RendaIncidenceCalculatorTest : StringSpec({

    val calculator = RendaIncidenceCalculator()

    "should always be applicable regardless of contribution date" {
        val consolidatingDate = LocalDate.parse("2025-12-14")
        val recentDate = LocalDate.parse("2023-12-14")
        val oldDate = LocalDate.parse("2020-01-01")
        val futureDate = LocalDate.parse("2025-01-01")

        calculator.isApplicable(consolidatingDate, recentDate) shouldBe true
        calculator.isApplicable(consolidatingDate, oldDate) shouldBe true
        calculator.isApplicable(consolidatingDate, futureDate) shouldBe true
    }

    "should calculate 22.5% for investments up to 180 days" {
        val consolidatingDate = LocalDate.parse("2025-12-14")
        val contributionDate = consolidatingDate.minus(1, DateTimeUnit.DAY)
        val result = calculator.resolve(consolidatingDate, contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe BigDecimal("22.50")
    }

    "should calculate 22.5% for investments exactly at 180 days" {
        val consolidatingDate = LocalDate.parse("2025-12-14")
        val contributionDate = consolidatingDate.minus(180, DateTimeUnit.DAY)
        val result = calculator.resolve(consolidatingDate, contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe BigDecimal("22.50")
    }

    "should calculate 20.0% for investments between 181 and 360 days" {
        val consolidatingDate = LocalDate.parse("2025-12-14")
        val contributionDate = consolidatingDate.minus(181, DateTimeUnit.DAY)
        val result = calculator.resolve(consolidatingDate, contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe BigDecimal("20.00")
    }

    "should calculate 20.0% for investments exactly at 360 days" {
        val consolidatingDate = LocalDate.parse("2025-12-14")
        val contributionDate = consolidatingDate.minus(360, DateTimeUnit.DAY)
        val result = calculator.resolve(consolidatingDate, contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe BigDecimal("20.00")
    }

    "should calculate 17.5% for investments between 361 and 720 days" {
        val consolidatingDate = LocalDate.parse("2025-12-14")
        val contributionDate = consolidatingDate.minus(361, DateTimeUnit.DAY)
        val result = calculator.resolve(consolidatingDate, contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe BigDecimal("17.50")
    }

    "should calculate 17.5% for investments exactly at 720 days" {
        val consolidatingDate = LocalDate.parse("2025-12-14")
        val contributionDate = consolidatingDate.minus(720, DateTimeUnit.DAY)
        val result = calculator.resolve(consolidatingDate, contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe BigDecimal("17.50")
    }

    "should calculate 15.0% for investments over 720 days" {
        val consolidatingDate = LocalDate.parse("2025-12-14")
        val contributionDate = consolidatingDate.minus(721, DateTimeUnit.DAY)
        val result = calculator.resolve(consolidatingDate, contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe BigDecimal("15.00")
    }

    "should calculate 15.0% for very old investments" {
        val consolidatingDate = LocalDate.parse("2025-12-14")
        val contributionDate = consolidatingDate.minus(1400, DateTimeUnit.DAY)
        val result = calculator.resolve(consolidatingDate, contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe BigDecimal("15.00")
    }
})
