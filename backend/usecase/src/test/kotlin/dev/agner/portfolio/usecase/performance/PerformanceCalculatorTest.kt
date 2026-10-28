package dev.agner.portfolio.usecase.performance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class PerformanceCalculatorTest : StringSpec({

    "decomposes a month into the return the contributions and income do not explain" {
        val decomposition = PerformanceCalculator.decompose(
            valueStart = BigDecimal("100000"),
            valueEnd = BigDecimal("103500"),
            netAportes = BigDecimal("2000"),
            netIncome = BigDecimal("500"),
        )

        decomposition.returnPercentage?.compareTo(BigDecimal("1.0000")) shouldBe 0
    }

    "has no return without a starting value" {
        PerformanceCalculator.decompose(
            valueStart = null,
            valueEnd = BigDecimal("103500"),
            netAportes = BigDecimal("0"),
            netIncome = BigDecimal("0"),
        ).returnPercentage shouldBe null

        PerformanceCalculator.decompose(
            valueStart = BigDecimal.ZERO,
            valueEnd = BigDecimal("103500"),
            netAportes = BigDecimal("0"),
            netIncome = BigDecimal("0"),
        ).returnPercentage shouldBe null
    }

    "a loss nets the contributions and income out too" {
        val decomposition = PerformanceCalculator.decompose(
            valueStart = BigDecimal("100000"),
            valueEnd = BigDecimal("98000"),
            netAportes = BigDecimal("1000"),
            netIncome = BigDecimal("100"),
        )

        decomposition.returnPercentage?.compareTo(BigDecimal("-3.1000")) shouldBe 0
    }

    "nets a slice's bookkeeping flow out of its return" {
        val decomposition = PerformanceCalculator.decompose(
            valueStart = BigDecimal("3000"),
            valueEnd = BigDecimal("4000"),
            netAportes = BigDecimal.ZERO,
            netIncome = BigDecimal.ZERO,
            flow = BigDecimal("1000"),
        )

        decomposition.flow.compareTo(BigDecimal("1000")) shouldBe 0
        decomposition.returnPercentage?.compareTo(BigDecimal("0.0000")) shouldBe 0
    }

    "adds a slice's bookkeeping outflow back to its return" {
        val decomposition = PerformanceCalculator.decompose(
            valueStart = BigDecimal("7000"),
            valueEnd = BigDecimal("6000"),
            netAportes = BigDecimal.ZERO,
            netIncome = BigDecimal.ZERO,
            flow = BigDecimal("-1000"),
        )

        decomposition.returnPercentage?.compareTo(BigDecimal("0.0000")) shouldBe 0
    }
})
