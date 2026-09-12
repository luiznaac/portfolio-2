package dev.agner.portfolio.usecase.monthlyclose

import dev.agner.portfolio.usecase.allocation.AllocationService
import dev.agner.portfolio.usecase.allocation.model.AllocationPlan
import dev.agner.portfolio.usecase.allocation.model.AssetClass.FIXED_INCOME
import dev.agner.portfolio.usecase.allocation.model.AssetClass.STOCKS
import dev.agner.portfolio.usecase.allocation.model.ClassNode
import dev.agner.portfolio.usecase.monthlyclose.model.MonthlyClose
import dev.agner.portfolio.usecase.monthlyclose.model.MonthlyCloseStatus.CLOSED
import dev.agner.portfolio.usecase.monthlyclose.model.MonthlyCloseStatus.OPEN
import dev.agner.portfolio.usecase.monthlyclose.repository.IMonthlyCloseRepository
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.SaleCeiling
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MonthlyCloseServiceTest : StringSpec({
    val repository = mockk<IMonthlyCloseRepository>()
    val allocationService = mockk<AllocationService>()
    val orderPlanService = mockk<OrderPlanService>()
    val clock = mockk<Clock>()

    val service = MonthlyCloseService(
        repository,
        allocationService,
        orderPlanService,
        clock,
    )

    beforeTest {
        // Each test stubs its own behavior; clearing keeps the exact-count verifies scoped to one
        // test instead of counting calls recorded by the tests before it.
        clearMocks(repository)
        every { clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        every { clock.zone } returns ZoneOffset.UTC
        coEvery { orderPlanService.transfersForMonth() } returns emptyList()
        coEvery { orderPlanService.refreshPlan() } returns OrderPlan(
            orders = emptyList(),
            transferProposals = emptyList(),
            saleCeiling = SaleCeiling(BigDecimal.ZERO, BigDecimal("20000.00"), BigDecimal("20000.00")),
        )
    }

    "current should open the current month" {
        val open = MonthlyClose(1, LocalDate(2026, 9, 1), OPEN, null)
        coEvery { repository.open(LocalDate(2026, 9, 1)) } returns open

        service.current() shouldBe open
    }

    "close should close the current month through a single repository call" {
        val month = LocalDate(2026, 9, 1)
        coEvery { repository.close(month) } returns MonthlyClose(1, month, CLOSED, null)

        val result = service.close()

        result.status shouldBe CLOSED
        // The stranded-pending expiry runs before counting, so a month whose leftovers were
        // auto-rejected closes even though transfersForMonth() might have rows.
        coVerify(exactly = 1) { orderPlanService.refreshPlan() }
        coVerify(exactly = 1) { repository.close(month) }
        coVerify(exactly = 0) { repository.open(any()) }
    }

    "close should propagate the already-closed conflict" {
        val month = LocalDate(2026, 9, 1)
        coEvery { repository.close(month) } throws MonthlyCloseAlreadyClosedException(month)

        shouldThrow<MonthlyCloseAlreadyClosedException> { service.close() }
    }

    "close should refuse to close with a pending transfer proposal" {
        coEvery { orderPlanService.transfersForMonth() } returns listOf(
            dev.agner.portfolio.usecase.order.model.TransferProposal(
                id = 1,
                month = LocalDate(2026, 9, 1),
                listedAssetId = 1,
                ticker = "ORVR3",
                fromStrategyId = 1,
                fromStrategyName = "Top",
                toStrategyId = 2,
                toStrategyName = "Small Caps",
                proposedQuantity = BigDecimal("9"),
                appliedQuantity = null,
                status = dev.agner.portfolio.usecase.order.model.TransferProposalStatus.PENDING,
                decidedAt = null,
            ),
        )

        shouldThrow<PendingTransferProposalsException> { service.close() }
    }

    "driftAlert should flag only classes whose drift exceeds the threshold" {
        coEvery { allocationService.currentPlan() } returns AllocationPlan(
            capital = BigDecimal("100000"),
            classes = listOf(
                // 2pp drift — under the 5pp default threshold
                ClassNode(STOCKS, BigDecimal("0.60"), BigDecimal("60000"), BigDecimal("62000")),
                // 10pp drift — over threshold
                ClassNode(FIXED_INCOME, BigDecimal("0.30"), BigDecimal("30000"), BigDecimal("20000")),
            ),
        )

        val alerts = service.driftAlert()

        alerts.map { it.assetClass } shouldBe listOf(FIXED_INCOME)
    }
})
