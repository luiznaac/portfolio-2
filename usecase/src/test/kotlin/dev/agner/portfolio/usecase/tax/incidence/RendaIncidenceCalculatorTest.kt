package dev.agner.portfolio.usecase.tax.incidence

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.LocalDate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RendaIncidenceCalculatorTest : StringSpec({

    val fixedInstant = Instant.parse("2023-12-15T10:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    val calculator = RendaIncidenceCalculator(fixedClock)

    "should always be applicable regardless of contribution date" {
        val recentDate = LocalDate.parse("2023-12-14")
        val oldDate = LocalDate.parse("2020-01-01")
        val futureDate = LocalDate.parse("2025-01-01")

        calculator.isApplicable(recentDate) shouldBe true
        calculator.isApplicable(oldDate) shouldBe true
        calculator.isApplicable(futureDate) shouldBe true
    }

    "should calculate 22.5% for investments up to 180 days" {
        val contributionDate = LocalDate.parse("2023-12-14") // 1 day ago
        val result = calculator.resolve(contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe 22.5
    }

    "should calculate 22.5% for investments exactly at 180 days" {
        val contributionDate = LocalDate.parse("2023-06-18") // 180 days ago
        val result = calculator.resolve(contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe 22.5
    }

    "should calculate 20.0% for investments between 181 and 360 days" {
        val contributionDate = LocalDate.parse("2023-06-17") // 181 days ago
        val result = calculator.resolve(contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe 20.0
    }

    "should calculate 20.0% for investments exactly at 360 days" {
        val contributionDate = LocalDate.parse("2022-12-20") // 360 days ago
        val result = calculator.resolve(contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe 20.0
    }

    "should calculate 17.5% for investments between 361 and 720 days" {
        val contributionDate = LocalDate.parse("2022-12-19") // 361 days ago
        val result = calculator.resolve(contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe 17.5
    }

    "should calculate 17.5% for investments exactly at 720 days" {
        val contributionDate = LocalDate.parse("2022-01-04") // 720 days ago
        val result = calculator.resolve(contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe 17.5
    }

    "should calculate 15.0% for investments over 720 days" {
        val contributionDate = LocalDate.parse("2021-12-24") // 721 days ago
        val result = calculator.resolve(contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe 15.0
    }

    "should calculate 15.0% for very old investments" {
        val contributionDate = LocalDate.parse("2020-01-01") // Over 1400 days ago
        val result = calculator.resolve(contributionDate)

        result.shouldBeInstanceOf<TaxIncidence.Renda>()
        result.rate shouldBe 15.0
    }
})
