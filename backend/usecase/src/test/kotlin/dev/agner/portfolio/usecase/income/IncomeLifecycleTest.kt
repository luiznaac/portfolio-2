package dev.agner.portfolio.usecase.income

import dev.agner.portfolio.usecase.commons.dayBefore
import dev.agner.portfolio.usecase.commons.nextDay
import dev.agner.portfolio.usecase.income.model.IncomeStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

// The Proventos lifecycle rule: a movement is announced while its payable date is ahead, payable on
// the day itself, and received once that day has passed — the file's net amount is what lands, so no
// receipt feed is needed to fold it in. Reconciled is the one stage a stored row owns (the
// Movimentações match), and it never regresses.
class IncomeLifecycleTest : DescribeSpec({

    describe("IncomeLifecycle.statusAt") {

        it("is announced while the payable date is ahead") {
            IncomeLifecycle.statusAt(IncomeStatus.ANNOUNCED, PAYABLE_DATE, today = PAYABLE_DATE.dayBefore()) shouldBe
                IncomeStatus.ANNOUNCED
        }

        it("is payable on the payable date") {
            IncomeLifecycle.statusAt(IncomeStatus.ANNOUNCED, PAYABLE_DATE, today = PAYABLE_DATE) shouldBe
                IncomeStatus.PAYABLE
        }

        it("is received the day after the payable date") {
            IncomeLifecycle.statusAt(IncomeStatus.ANNOUNCED, PAYABLE_DATE, today = PAYABLE_DATE.nextDay()) shouldBe
                IncomeStatus.RECEIVED
        }

        it("keeps a stored reconciled row reconciled, even before its payable date") {
            IncomeLifecycle.statusAt(
                IncomeStatus.RECONCILED,
                PAYABLE_DATE,
                today = PAYABLE_DATE.dayBefore(),
            ) shouldBe IncomeStatus.RECONCILED
        }

        it("keeps a stored reconciled row reconciled after its payable date") {
            IncomeLifecycle.statusAt(
                IncomeStatus.RECONCILED,
                PAYABLE_DATE,
                today = PAYABLE_DATE.nextDay(),
            ) shouldBe IncomeStatus.RECONCILED
        }
    }
})

private val PAYABLE_DATE = LocalDate.parse("2026-11-03")
