package dev.agner.portfolio.usecase.tax.stepup

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class StepUpPlannerTest : StringSpec({
    val planner = StepUpPlanner()
    // a Tuesday, so D+1 doesn't cross a weekend
    val today = LocalDate(2026, 9, 8)

    "should prioritize the candidate with the highest unit gain ratio first" {
        val candidates = listOf(
            StepUpCandidate(1, "AAAA3", BigDecimal("100"), BigDecimal("10.00"), BigDecimal("12.00")), // 16.7%
            StepUpCandidate(2, "BBBB3", BigDecimal("100"), BigDecimal("10.00"), BigDecimal("20.00")), // 50%
        )

        val plan = planner.plan(candidates, remainingCeiling = BigDecimal("1000"), today = today)

        plan.suggestions[0].ticker shouldBe "BBBB3"
    }

    "should cap the suggested quantity at what the remaining ceiling allows" {
        val candidates = listOf(
            StepUpCandidate(1, "AAAA3", BigDecimal("100"), BigDecimal("10.00"), BigDecimal("20.00")),
        )

        val plan = planner.plan(candidates, remainingCeiling = BigDecimal("500"), today = today)

        plan.suggestions[0].quantity shouldBe BigDecimal("25")
        plan.suggestions[0].notional shouldBe BigDecimal("500.00")
        plan.suggestions[0].realizedGain shouldBe BigDecimal("250.00")
        plan.remainingCeilingAfter shouldBe BigDecimal("0.00")
    }

    "should suggest the rebuy date as the next day" {
        val candidates = listOf(
            StepUpCandidate(1, "AAAA3", BigDecimal("10"), BigDecimal("10.00"), BigDecimal("20.00")),
        )

        val plan = planner.plan(candidates, remainingCeiling = BigDecimal("1000"), today = today)

        plan.suggestions[0].rebuyDate shouldBe LocalDate(2026, 9, 9)
    }

    "should roll the rebuy date to Monday when D+1 falls on a weekend" {
        val friday = LocalDate(2026, 9, 11)
        val candidates = listOf(
            StepUpCandidate(1, "AAAA3", BigDecimal("10"), BigDecimal("10.00"), BigDecimal("20.00")),
        )

        val plan = planner.plan(candidates, remainingCeiling = BigDecimal("1000"), today = friday)

        plan.suggestions[0].rebuyDate shouldBe LocalDate(2026, 9, 14)
    }

    "should skip candidates with no unrealized gain" {
        val candidates = listOf(
            StepUpCandidate(1, "AAAA3", BigDecimal("10"), BigDecimal("20.00"), BigDecimal("15.00")),
        )

        val plan = planner.plan(candidates, remainingCeiling = BigDecimal("1000"), today = today)

        plan.suggestions shouldBe emptyList()
        plan.totalRealizedGain shouldBe BigDecimal("0.00")
    }

    "should propose nothing when there is no ceiling left" {
        val candidates = listOf(
            StepUpCandidate(1, "AAAA3", BigDecimal("10"), BigDecimal("10.00"), BigDecimal("20.00")),
        )

        val plan = planner.plan(candidates, remainingCeiling = BigDecimal.ZERO, today = today)

        plan.suggestions shouldBe emptyList()
    }
})
