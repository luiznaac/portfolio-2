package dev.agner.portfolio.usecase.tax.capitalgains

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class CapitalGainsCalculatorTest : StringSpec({
    val calculator = CapitalGainsCalculator()

    "should exempt a stock month with proceeds at or under R$20,000" {
        val sales = listOf(
            TaxableSale(
                LocalDate(2026, 8, 10),
                isFii = false,
                proceeds = BigDecimal("19000"),
                costBasis = BigDecimal("15000"),
            ),
        )

        val result = calculator.calculate(sales)

        result shouldBe listOf(
            MonthlyGainFixture.of(
                month = LocalDate(2026, 8, 1),
                isFii = false,
                proceeds = BigDecimal("19000.00"),
                grossGain = BigDecimal("4000.00"),
                exempt = true,
                lossCompensated = BigDecimal("0.00"),
                taxableGain = BigDecimal("0.00"),
                taxDue = BigDecimal("0.00"),
                lossCarriedForward = BigDecimal("0.00"),
            ),
        )
    }

    "should tax the whole gain at 15% when stock proceeds exceed the ceiling" {
        val sales = listOf(
            TaxableSale(
                LocalDate(2026, 8, 10),
                isFii = false,
                proceeds = BigDecimal("26549.84"),
                costBasis = BigDecimal("20000.00"),
            ),
        )

        val result = calculator.calculate(sales)

        result[0].exempt shouldBe false
        result[0].taxableGain shouldBe BigDecimal("6549.84")
        result[0].taxDue shouldBe BigDecimal("982.48")
    }

    "should never exempt a FII sale regardless of proceeds" {
        val sales = listOf(
            TaxableSale(
                LocalDate(2026, 8, 10),
                isFii = true,
                proceeds = BigDecimal("5000"),
                costBasis = BigDecimal("4000"),
            ),
        )

        val result = calculator.calculate(sales)

        result[0].exempt shouldBe false
        result[0].taxableGain shouldBe BigDecimal("1000.00")
        result[0].taxDue shouldBe BigDecimal("200.00")
    }

    "should carry a loss forward and compensate it against a later non-exempt gain" {
        val sales = listOf(
            TaxableSale(
                LocalDate(2026, 7, 5),
                isFii = false,
                proceeds = BigDecimal("25000"),
                costBasis = BigDecimal("28000"),
            ),
            TaxableSale(
                LocalDate(2026, 8, 5),
                isFii = false,
                proceeds = BigDecimal("25000"),
                costBasis = BigDecimal("20000"),
            ),
        )

        val result = calculator.calculate(sales)

        result[0].grossGain shouldBe BigDecimal("-3000.00")
        result[0].lossCarriedForward shouldBe BigDecimal("3000.00")

        result[1].grossGain shouldBe BigDecimal("5000.00")
        result[1].lossCompensated shouldBe BigDecimal("3000.00")
        result[1].taxableGain shouldBe BigDecimal("2000.00")
        result[1].lossCarriedForward shouldBe BigDecimal("0.00")
    }

    "should keep a loss available even in an exempt month, for a later month to use" {
        val sales = listOf(
            TaxableSale(
                LocalDate(2026, 7, 5),
                isFii = false,
                proceeds = BigDecimal("10000"),
                costBasis = BigDecimal("12000"),
            ),
            TaxableSale(
                LocalDate(2026, 8, 5),
                isFii = false,
                proceeds = BigDecimal("30000"),
                costBasis = BigDecimal("28000"),
            ),
        )

        val result = calculator.calculate(sales)

        result[0].exempt shouldBe true
        result[0].lossCarriedForward shouldBe BigDecimal("2000.00")

        result[1].lossCompensated shouldBe BigDecimal("2000.00")
        result[1].taxableGain shouldBe BigDecimal("0.00")
    }

    "should keep stock and FII loss carryforwards independent" {
        val sales = listOf(
            TaxableSale(
                LocalDate(2026, 7, 5),
                isFii = false,
                proceeds = BigDecimal("10000"),
                costBasis = BigDecimal("12000"),
            ),
            TaxableSale(
                LocalDate(2026, 8, 5),
                isFii = true,
                proceeds = BigDecimal("5000"),
                costBasis = BigDecimal("4000"),
            ),
        )

        val result = calculator.calculate(sales)

        val fiiMonth = result.first { it.isFii }
        fiiMonth.lossCompensated shouldBe BigDecimal("0.00")
        fiiMonth.taxableGain shouldBe BigDecimal("1000.00")
    }
})

// Small helper so the "should exempt" test reads as one literal instead of field-by-field asserts.
private object MonthlyGainFixture {
    fun of(
        month: LocalDate,
        isFii: Boolean,
        proceeds: BigDecimal,
        grossGain: BigDecimal,
        exempt: Boolean,
        lossCompensated: BigDecimal,
        taxableGain: BigDecimal,
        taxDue: BigDecimal,
        lossCarriedForward: BigDecimal,
    ) = dev.agner.portfolio.usecase.tax.capitalgains.model.MonthlyCapitalGain(
        month, isFii, proceeds, grossGain, exempt, lossCompensated, taxableGain, taxDue, lossCarriedForward,
    )
}
