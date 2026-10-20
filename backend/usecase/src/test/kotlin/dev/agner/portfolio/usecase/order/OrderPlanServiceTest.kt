package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.classTarget
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategyTarget
import dev.agner.portfolio.usecase.tickerTarget
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The orchestration seam: the ideal chain is fed by the open month's targets, the projected
// positions and the latest stored close for every ticker the plan can leaf on.
class OrderPlanServiceTest : StringSpec({
    val months = mockk<MonthService>(relaxed = true)
    val positions = mockk<PositionService>(relaxed = true)
    val quotes = mockk<QuoteService>(relaxed = true)
    val service = OrderPlanService(months, positions, quotes)

    beforeTest { clearAllMocks() }

    "returns null when no month is open and reads no book" {
        coEvery { months.fetchCurrent() } returns null

        service.computePlan(OrderPlanPhase.IDEALS) shouldBe null

        coVerify(exactly = 0) { positions.fetchAll() }
        coVerify(exactly = 0) { quotes.latestClose(any()) }
    }

    "values a position at its current quote and targets a ticker with no position from its quote" {
        coEvery { months.fetchCurrent() } returns currentMonth
        coEvery { positions.fetchAll() } returns listOf(Position("VALE3", BigDecimal("10"), BigDecimal("8")))
        coEvery { quotes.latestClose("VALE3") } returns BigDecimal("12")
        coEvery { quotes.latestClose("ITUB4") } returns BigDecimal("20")

        val plan = service.computePlan(OrderPlanPhase.CANDIDATES)!!

        plan.phase shouldBe OrderPlanPhase.CANDIDATES
        plan.candidates.associate { it.ticker to (it.side to it.quantity.plain()) } shouldBe mapOf(
            "ITUB4" to (OrderSide.NEW_ENTRY to "250"),
            "VALE3" to (OrderSide.BUY to "406"),
        )
        plan.candidates.single { it.ticker == "VALE3" }.price!!.plain() shouldBe "12"
        coVerify(exactly = 1) { positions.fetchAll() }
    }

    "fetches the quote of every targeted ticker, including one with no position" {
        coEvery { months.fetchCurrent() } returns currentMonth
        coEvery { positions.fetchAll() } returns emptyList()
        coEvery { quotes.latestClose("VALE3") } returns null
        coEvery { quotes.latestClose("ITUB4") } returns null

        service.computePlan(OrderPlanPhase.IDEALS)

        coVerify { quotes.latestClose("VALE3") }
        coVerify { quotes.latestClose("ITUB4") }
    }
})

private val currentMonth = CurrentMonth(
    month = Month(
        id = 8,
        period = YearMonth.parse("2026-10"),
        capital = BigDecimal("10000.00"),
        floor = BigDecimal("0.00"),
        status = MonthStatus.OPEN,
    ),
    targets = Targets(
        classes = listOf(classTarget(AssetClass.ACOES, "100")),
        strategies = listOf(strategyTarget(AssetClass.ACOES, "Top", "100")),
        subClasses = emptyList(),
        tickers = listOf(
            tickerTarget("Top", "VALE3", "50"),
            tickerTarget("Top", "ITUB4", "50"),
        ),
    ),
)
