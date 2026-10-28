package dev.agner.portfolio.usecase.performance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class XirrCalculatorTest : StringSpec({

    "solves a one-year ten-percent return" {
        val rate = XirrCalculator.calculate(
            listOf(
                XirrFlow(LocalDate.parse("2026-01-01"), BigDecimal("-1000")),
                XirrFlow(LocalDate.parse("2027-01-01"), BigDecimal("1100")),
            ),
        )

        rate?.compareTo(BigDecimal("10.0000")) shouldBe 0
    }

    "accounts for a mid-period contribution" {
        val rate = XirrCalculator.calculate(
            listOf(
                XirrFlow(LocalDate.parse("2026-01-01"), BigDecimal("-1000")),
                XirrFlow(LocalDate.parse("2026-07-01"), BigDecimal("-1000")),
                XirrFlow(LocalDate.parse("2027-01-01"), BigDecimal("2000")),
            ),
        )

        // Both legs earn nothing in this flow set, so the annualized rate is zero.
        rate?.compareTo(BigDecimal("0.0000")) shouldBe 0
    }

    "returns null when the flows cannot produce a rate" {
        XirrCalculator.calculate(emptyList()) shouldBe null
        XirrCalculator.calculate(listOf(XirrFlow(LocalDate.parse("2026-01-01"), BigDecimal("-1000")))) shouldBe null
        XirrCalculator.calculate(
            listOf(
                XirrFlow(LocalDate.parse("2026-01-01"), BigDecimal("-1000")),
                XirrFlow(LocalDate.parse("2027-01-01"), BigDecimal("-100")),
            ),
        ) shouldBe null
    }
})
