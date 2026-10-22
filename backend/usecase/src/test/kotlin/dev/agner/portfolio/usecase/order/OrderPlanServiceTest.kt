package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.classTarget
import dev.agner.portfolio.usecase.listedasset.AttributionService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.order.model.AttributionSide
import dev.agner.portfolio.usecase.order.model.EquitiesOrderPlan
import dev.agner.portfolio.usecase.order.model.FundingSource
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.portfolio.AllocationService
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.Allocation
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
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
// positions and the latest stored close for every ticker the plan can leaf on; the funding pass also
// gets the whole book's value, the strategies' NEUTRO slices and the attribution ledger.
class OrderPlanServiceTest : StringSpec({
    val months = mockk<MonthService>(relaxed = true)
    val positions = mockk<PositionService>(relaxed = true)
    val quotes = mockk<QuoteService>(relaxed = true)
    val allocations = mockk<AllocationService>(relaxed = true)
    val strategies = mockk<StrategyService>(relaxed = true)
    val attributions = mockk<AttributionService>(relaxed = true)
    val service = OrderPlanService(months, positions, quotes, allocations, strategies, attributions)

    beforeTest { clearAllMocks() }

    "returns null when no month is open and reads no book" {
        coEvery { months.fetchCurrent() } returns null

        service.computePlan(OrderPlanPhase.IDEALS) shouldBe null

        coVerify(exactly = 0) { positions.fetchAll() }
        coVerify(exactly = 0) { quotes.latestClose(any()) }
        coVerify(exactly = 0) { allocations.fetchCurrent() }
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
        // A shallower phase never touches the whole-book valuation.
        coVerify(exactly = 0) { allocations.fetchCurrent() }
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

    "feeds the funding pass the whole-book cash, the NEUTRO slices and the attribution ledger" {
        coEvery { months.fetchCurrent() } returns currentMonth
        coEvery { allocations.fetchCurrent() } returns allocation(actual = "120")
        coEvery { positions.fetchAll() } returns listOf(Position("VALE3", BigDecimal("10"), BigDecimal("8")))
        coEvery { quotes.latestClose("VALE3") } returns BigDecimal("12")
        coEvery { quotes.latestClose("ITUB4") } returns BigDecimal("20")
        coEvery { strategies.fetchNeutroSlices() } returns setOf(StrategyTicker("Top", "ITUB4"))
        coEvery { attributions.fetchLedger() } returns mapOf(StrategyTicker("Top", "VALE3") to BigDecimal("5"))

        val plan = service.computePlan(OrderPlanPhase.FUNDING)!!

        val slices = plan.classes.filterIsInstance<EquitiesOrderPlan>()
            .flatMap { it.strategies }
            .flatMap { it.tickers }
            .associateBy { it.ticker }
        slices.getValue("ITUB4").neutro shouldBe true
        slices.getValue("VALE3").attributedQuantity.plain() shouldBe "5"
        plan.funding!!.buyAmount.plain() shouldBe "4872"
        plan.funding!!.neutroDeficit.plain() shouldBe "5000"
    }

    "adds funding sells to the plan when the Uninvested Balance cannot cover the buys" {
        coEvery { months.fetchCurrent() } returns currentMonth
        coEvery { allocations.fetchCurrent() } returns allocation(actual = "6000")
        coEvery { positions.fetchAll() } returns listOf(Position("VALE3", BigDecimal("500"), BigDecimal("8")))
        coEvery { quotes.latestClose("VALE3") } returns BigDecimal("12")
        coEvery { quotes.latestClose("ITUB4") } returns BigDecimal("20")

        val plan = service.computePlan(OrderPlanPhase.FUNDING)!!

        plan.candidates.single { it.ticker == "ITUB4" }.let {
            it.side shouldBe OrderSide.NEW_ENTRY
            it.quantity.plain() shouldBe "250"
        }

        val sell = plan.candidates.single { it.side == OrderSide.SELL } as OrderCandidate.FundingSell
        sell.ticker shouldBe "VALE3"
        sell.quantity.plain() shouldBe "83"
        sell.fundingSource shouldBe FundingSource.SURPLUS
        plan.funding!!.buyAmount.plain() shouldBe "5000"
        plan.funding!!.sellAmount.plain() shouldBe "996"
        plan.funding!!.unfundedAmount.plain() shouldBe "4"
    }

    "scales the buys and aims the attribution at the full phase" {
        coEvery { months.fetchCurrent() } returns currentMonth
        coEvery { allocations.fetchCurrent() } returns allocation(actual = "6000")
        coEvery { positions.fetchAll() } returns listOf(Position("VALE3", BigDecimal("500"), BigDecimal("8")))
        coEvery { quotes.latestClose("VALE3") } returns BigDecimal("12")
        coEvery { quotes.latestClose("ITUB4") } returns BigDecimal("20")

        val plan = service.computePlan(OrderPlanPhase.FULL)!!

        plan.phase shouldBe OrderPlanPhase.FULL
        plan.candidates.single { it.ticker == "ITUB4" }.quantity.plain() shouldBe "249"
        plan.proRata!!.factor.plain() shouldBe "0.9992"
        plan.proRata!!.buyAmount.plain() shouldBe "4980"
        plan.funding!!.buyAmount.plain() shouldBe "4980"
        plan.funding!!.unfundedAmount.plain() shouldBe "20"
        plan.attribution.map { Triple(it.ticker, it.strategy, it.side) } shouldBe listOf(
            Triple("ITUB4", "Top", AttributionSide.ALLOCATE),
        )
        plan.attribution.single().quantity.plain() shouldBe "249"
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

private fun allocation(actual: String) = Allocation(
    period = YearMonth.parse("2026-10"),
    capital = BigDecimal("10000.00"),
    floor = BigDecimal.ZERO,
    ideal = BigDecimal("10000.00"),
    actual = BigDecimal(actual),
    deficit = BigDecimal.ZERO,
    drift = BigDecimal.ZERO,
    unattributed = BigDecimal.ZERO,
    classes = emptyList(),
)
