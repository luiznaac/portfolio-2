package dev.agner.portfolio.usecase.performance.benchmark

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class BenchmarkCalculatorTest : StringSpec({
    val january = LocalDate.parse("2026-01-01")
    val januaryEnd = LocalDate.parse("2026-01-31")

    "compounds the CDI daily rate over the month" {
        val result = BenchmarkCalculator.returnFor(
            BenchmarkIndex.CDI,
            listOf(
                BenchmarkObservation(LocalDate.parse("2026-01-02"), BigDecimal("0.05")),
                BenchmarkObservation(LocalDate.parse("2026-01-03"), BigDecimal("0.05")),
            ),
            january,
            januaryEnd,
        )

        // (1.0005 * 1.0005 - 1) * 100 = 0.100025 -> 0.1000 at the benchmark scale.
        result?.compareTo(BigDecimal("0.1000")) shouldBe 0
    }

    "compounds the monthly IPCA variation" {
        val result = BenchmarkCalculator.returnFor(
            BenchmarkIndex.IPCA,
            listOf(BenchmarkObservation(LocalDate.parse("2026-01-10"), BigDecimal("0.30"))),
            january,
            januaryEnd,
        )

        result?.compareTo(BigDecimal("0.3000")) shouldBe 0
    }

    "reads the IBOV index level against the prior close" {
        val result = BenchmarkCalculator.returnFor(
            BenchmarkIndex.IBOV,
            listOf(
                BenchmarkObservation(LocalDate.parse("2025-12-30"), BigDecimal("100000")),
                BenchmarkObservation(LocalDate.parse("2026-01-30"), BigDecimal("102000")),
            ),
            january,
            januaryEnd,
        )

        result?.compareTo(BigDecimal("2.0000")) shouldBe 0
    }

    "has no benchmark return without the observations it needs" {
        BenchmarkCalculator.returnFor(BenchmarkIndex.IBOV, emptyList(), january, januaryEnd) shouldBe null
        BenchmarkCalculator.returnFor(
            BenchmarkIndex.IBOV,
            listOf(BenchmarkObservation(LocalDate.parse("2026-01-30"), BigDecimal("102000"))),
            january,
            januaryEnd,
        ) shouldBe null
    }
})
