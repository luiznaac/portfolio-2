package dev.agner.portfolio.usecase.tax.incidence

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class IOFIncidenceCalculatorTest : StringSpec({

    val fixedInstant = Instant.parse("2023-12-15T10:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    val calculator = IOFIncidenceCalculator(fixedClock)

    "should not be applicable when contribution date is exactly 30 days ago" {
        val contributionDate = LocalDate.parse("2023-11-15") // 30 days ago
        calculator.isApplicable(contributionDate) shouldBe false
    }

    "should calculate correct IOF rates for all valid days" {
        val expectedRates = mapOf(
            1 to 96.0, 2 to 93.0, 3 to 90.0, 4 to 86.0, 5 to 83.0,
            6 to 80.0, 7 to 76.0, 8 to 73.0, 9 to 70.0, 10 to 66.0,
            11 to 63.0, 12 to 60.0, 13 to 56.0, 14 to 53.0, 15 to 50.0,
            16 to 46.0, 17 to 43.0, 18 to 40.0, 19 to 36.0, 20 to 33.0,
            21 to 30.0, 22 to 26.0, 23 to 23.0, 24 to 20.0, 25 to 16.0,
            26 to 13.0, 27 to 10.0, 28 to 6.0, 29 to 3.0
        )

        expectedRates.forEach { (day, expectedRate) ->
            val contributionDate = LocalDate.parse("2023-12-15").minus(day, DateTimeUnit.DAY)
            val result = calculator.resolve(contributionDate)

            calculator.isApplicable(contributionDate) shouldBe true
            result.shouldBeInstanceOf<TaxIncidence.IOF>()
            result.rate shouldBe expectedRate
        }
    }

    "should throw IllegalArgumentException for invalid days (30 or more)" {
        val contributionDate = LocalDate.parse("2023-11-15") // 30 days ago
        calculator.isApplicable(contributionDate) shouldBe false

        shouldThrow<IllegalArgumentException> {
            calculator.resolve(contributionDate)
        }
    }
})
