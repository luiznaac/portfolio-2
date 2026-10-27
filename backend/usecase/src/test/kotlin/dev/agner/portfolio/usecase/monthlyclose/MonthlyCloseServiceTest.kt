package dev.agner.portfolio.usecase.monthlyclose

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.closeStateFixture
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.income.IncomeService
import dev.agner.portfolio.usecase.income.model.Income
import dev.agner.portfolio.usecase.income.model.IncomeStatus
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.model.Provenance
import dev.agner.portfolio.usecase.income.model.TaxNature
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.monthFixture
import dev.agner.portfolio.usecase.monthlyclose.model.AttributionSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
import dev.agner.portfolio.usecase.monthlyclose.repository.ISnapshotRepository
import dev.agner.portfolio.usecase.order.OrderLifecycleService
import dev.agner.portfolio.usecase.order.model.OrderList
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.SuggestedOrder
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferStatus
import dev.agner.portfolio.usecase.order.model.UnplannedTrade
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.portfolio.AllocationService
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.Allocation
import dev.agner.portfolio.usecase.portfolio.model.ClassAllocation
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import dev.agner.portfolio.usecase.tax.StockTaxService
import dev.agner.portfolio.usecase.tax.model.CarryBucket
import dev.agner.portfolio.usecase.tax.model.ExemptionMeter
import dev.agner.portfolio.usecase.tax.model.RealizedKind
import dev.agner.portfolio.usecase.tax.model.StockTaxReport
import dev.agner.portfolio.usecase.transferProposal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The close's orchestration seam: refresh the plan first (it decides the sub-threshold transfers),
// refuse when the refreshed inbox still has a pending transfer, otherwise lock the month and persist
// the immutable snapshot of everything the month ended with.
class MonthlyCloseServiceTest : StringSpec({
    val months = mockk<MonthService>(relaxed = true)
    val orders = mockk<OrderLifecycleService>(relaxed = true)
    val allocations = mockk<AllocationService>(relaxed = true)
    val positions = mockk<PositionService>(relaxed = true)
    val income = mockk<IncomeService>(relaxed = true)
    val tax = mockk<StockTaxService>(relaxed = true)
    val attributions = mockk<IAttributionRepository>(relaxed = true)
    val strategies = mockk<IStrategyRepository>(relaxed = true)
    val corporateActions = mockk<ICorporateActionRepository>(relaxed = true)
    val quotes = mockk<QuoteService>(relaxed = true)
    val snapshots = mockk<ISnapshotRepository>(relaxed = true)
    val service = MonthlyCloseService(
        months = months,
        orders = orders,
        allocations = allocations,
        positions = positions,
        income = income,
        tax = tax,
        attributions = attributions,
        strategies = strategies,
        corporateActions = corporateActions,
        quotes = quotes,
        snapshots = snapshots,
        transaction = immediateTransactions,
    )

    beforeTest {
        clearAllMocks()
        coEvery { months.fetchCurrent() } returns currentMonth
        coEvery { months.fetchClosed() } returns emptyList()
        coEvery { orders.refresh() } returns orderList()
        coEvery { orders.fetch() } returns orderList()
        coEvery { allocations.fetchCurrent() } returns allocation
        coEvery { tax.fetchCurrent() } returns taxReport
        coEvery { positions.fetchAll() } returns listOf(position("VALE3"), position("ITUB4"))
        coEvery { strategies.fetchAll() } returns listOf(strategy(1, "Top Dividendos"))
        coEvery { attributions.fetchAll() } returns listOf(Attribution("ITUB4", 1, BigDecimal("40")))
        coEvery { quotes.latestClose("ITUB4") } returns BigDecimal("10")
        coEvery { quotes.latestClose("VALE3") } returns BigDecimal("12")
        coEvery { corporateActions.fetchApplied() } returns emptyList()
        coEvery { snapshots.fetchAll() } returns emptyList()
        coEvery { income.fetchAll() } returns listOf(octoberIncome, septemberIncome)
        coEvery { months.close(month.id) } returns closedMonth
        coEvery { snapshots.save(month.id, any()) } returns persistedSnapshot
    }

    "returns null when no month is open and writes nothing" {
        coEvery { months.fetchCurrent() } returns null

        service.closeCurrent() shouldBe null

        coVerify(exactly = 0) { orders.refresh() }
        coVerify(exactly = 0) { snapshots.save(any(), any()) }
    }

    "blocks the close when the refreshed inbox still has a pending transfer" {
        val pending = transferProposal(7, TransferStatus.PENDING)
        coEvery { orders.refresh() } returns orderList(
            transfers = listOf(pending, transferProposal(8, TransferStatus.APPROVED)),
        )

        val error = shouldThrow<PendingTransfersException> { service.closeCurrent() }

        error.pending shouldBe listOf(pending)
        coVerify(exactly = 0) { months.close(any()) }
        coVerify(exactly = 0) { snapshots.save(any(), any()) }
    }

    "re-checks the inbox inside the close transaction" {
        coEvery { orders.fetch() } returns orderList(
            transfers = listOf(transferProposal(9, TransferStatus.PENDING)),
        )

        shouldThrow<PendingTransfersException> { service.closeCurrent() }

        coVerify(exactly = 0) { months.close(any()) }
        coVerify(exactly = 0) { snapshots.save(any(), any()) }
    }

    "locks the month and persists the whole close state once the inbox is decided" {
        val state = slot<CloseState>()
        coEvery { snapshots.save(month.id, capture(state)) } answers {
            Snapshot(id = 9, monthId = month.id, state = state.captured, createdAt = closedAt)
        }

        service.closeCurrent()!!.id shouldBe 9

        coVerify { months.close(month.id) }
        val persisted = state.captured
        persisted.period shouldBe period
        persisted.capital.plain() shouldBe "245000"
        persisted.floor.plain() shouldBe "20000"
        persisted.caixa.plain() shouldBe "45000"
        persisted.holdings.map { it.ticker } shouldBe listOf("ITUB4", "VALE3")
        persisted.holdings.first().averagePrice.plain() shouldBe "10"
        persisted.attributions.single().strategy shouldBe "Top Dividendos"
        persisted.attributions.single().quantity.plain() shouldBe "40"
        persisted.classes.single().assetClass shouldBe AssetClass.ACOES
        persisted.classes.single().actual.plain() shouldBe "120000"
        persisted.classes.single().unattributed.plain() shouldBe "119600"
        persisted.strategies.single().strategyId shouldBe 1
        persisted.strategies.single().strategy shouldBe "Top Dividendos"
        persisted.strategies.single().kind shouldBe StrategyKind.REPORT_DRIVEN
        persisted.strategies.single().valueEnd.plain() shouldBe "400"
        persisted.strategies.single().flow.plain() shouldBe "0"
        persisted.income.gross.plain() shouldBe "100"
        persisted.income.retained.plain() shouldBe "15"
        persisted.income.net.plain() shouldBe "85"
        persisted.incomeByTicker.single().ticker shouldBe "ITUB4"
        persisted.incomeByTicker.single().net.plain() shouldBe "85"
        persisted.orders.orders.single().ticker shouldBe "ITUB4"
        persisted.orders.transfers.single().status shouldBe TransferStatus.APPROVED
        persisted.orders.unplannedTrades.single().ticker shouldBe "CURY3"
        persisted.tax.meter shouldBe taxReport.meter
        persisted.tax.carry shouldBe taxReport.carry
    }

    "freezes the bucket slice and nets each slice's attribution change against the previous close" {
        coEvery { snapshots.fetchAll() } returns listOf(previousSnapshot)
        coEvery { strategies.fetchAll() } returns listOf(
            strategy(1, "Top Dividendos"),
            Strategy(id = 2, name = "Legado", assetClass = AssetClass.ACOES, kind = StrategyKind.MANUAL),
        )
        coEvery { attributions.fetchAll() } returns listOf(
            Attribution("ITUB4", 1, BigDecimal("40")),
            Attribution("VALE3", 2, BigDecimal("40")),
        )
        val state = slot<CloseState>()
        coEvery { snapshots.save(month.id, capture(state)) } answers {
            Snapshot(id = 9, monthId = month.id, state = state.captured, createdAt = closedAt)
        }

        service.closeCurrent()!!.id shouldBe 9

        val slices = state.captured.strategies.associateBy { it.strategyId }
        slices.getValue(1).valueEnd.plain() shouldBe "400"
        slices.getValue(1).flow.plain() shouldBe "100"
        slices.getValue(2).strategy shouldBe "Legado"
        slices.getValue(2).kind shouldBe StrategyKind.MANUAL
        slices.getValue(2).valueEnd.plain() shouldBe "480"
        slices.getValue(2).flow.plain() shouldBe "480"
        state.captured.classes.single().actual.plain() shouldBe "120000"
        state.captured.classes.single().unattributed.plain() shouldBe "119120"
    }

    "nets a slice whose row was deleted at zero against the previous close" {
        coEvery { snapshots.fetchAll() } returns listOf(previousSnapshot)
        coEvery { attributions.fetchAll() } returns emptyList()
        val state = slot<CloseState>()
        coEvery { snapshots.save(month.id, capture(state)) } answers {
            Snapshot(id = 9, monthId = month.id, state = state.captured, createdAt = closedAt)
        }

        service.closeCurrent()!!.id shouldBe 9

        val slice = state.captured.strategies.single()
        slice.valueEnd.plain() shouldBe "0"
        slice.flow.plain() shouldBe "-300"
    }

    "does not read a corporate action's quantity scaling as a flow" {
        coEvery { snapshots.fetchAll() } returns listOf(previousSnapshot)
        coEvery { attributions.fetchAll() } returns listOf(Attribution("ITUB4", 1, BigDecimal("60")))
        coEvery { corporateActions.fetchApplied() } returns listOf(splitAction)
        val state = slot<CloseState>()
        coEvery { snapshots.save(month.id, capture(state)) } answers {
            Snapshot(id = 9, monthId = month.id, state = state.captured, createdAt = closedAt)
        }

        service.closeCurrent()!!.id shouldBe 9

        val slice = state.captured.strategies.single()
        slice.valueEnd.plain() shouldBe "600"
        slice.flow.plain() shouldBe "0"
    }

    "returns null and persists no snapshot when the month is no longer open" {
        coEvery { months.close(month.id) } returns null

        service.closeCurrent() shouldBe null

        coVerify(exactly = 0) { snapshots.save(any(), any()) }
    }

    "fetchClosedMonths reads the closed months from the month service" {
        val closed = listOf(closedMonth)
        coEvery { months.fetchClosed() } returns closed

        service.fetchClosedMonths() shouldBe closed
    }

    "fetchSnapshot reads the closed month's frozen state from the snapshot ledger" {
        coEvery { snapshots.fetchByMonth(7) } returns persistedSnapshot

        service.fetchSnapshot(7) shouldBe persistedSnapshot
    }

    "fetchSnapshot returns null for a month that was never closed" {
        coEvery { snapshots.fetchByMonth(99) } returns null

        service.fetchSnapshot(99) shouldBe null
    }
})

private val immediateTransactions = object : ITransactionTemplate {
    override suspend fun <T> execute(block: suspend () -> T): T = block()
}

private val period = YearMonth.parse("2026-10")
private val closedAt = LocalDateTime.parse("2026-10-31T18:00:00")

private val month = monthFixture()
private val closedMonth = monthFixture(MonthStatus.CLOSED)

private val currentMonth = CurrentMonth(
    month = month,
    targets = Targets(classes = emptyList(), strategies = emptyList(), subClasses = emptyList(), tickers = emptyList()),
)

private val persistedSnapshot = Snapshot(id = 1, monthId = 7, state = closeStateFixture(), createdAt = closedAt)

// The close before the month under test: ITUB4 was 30 units into Top Dividendos, so the current
// 40-unit slice carries a 10-unit bookkeeping flow valued at the month's close.
private val previousSnapshot = Snapshot(
    id = 1,
    monthId = 6,
    state = closeStateFixture().copy(
        period = YearMonth.parse("2026-09"),
        attributions = listOf(AttributionSnapshot("ITUB4", 1, "Top Dividendos", BigDecimal("30"))),
    ),
    createdAt = closedAt,
)

// A 2:1 split inside the month: custody and every slice double, so the 60-unit slice is the same
// book as the prior 30 units and carries no flow.
private val splitAction = CorporateAction(
    id = 1,
    ticker = "ITUB4",
    kind = CorporateActionKind.SPLIT,
    exDate = LocalDate.parse("2026-10-15"),
    approvalDate = null,
    ratio = BigDecimal("2"),
    declaredValuePerShare = null,
    targetTicker = null,
    source = CorporateActionSource.B3_MOVEMENTS,
    processNumber = null,
    rawType = null,
    ordinal = 1,
    status = CorporateActionStatus.APPLIED,
)

private val allocation = Allocation(
    period = period,
    capital = BigDecimal("245000.00"),
    floor = BigDecimal("20000.00"),
    ideal = BigDecimal("245000.00"),
    actual = BigDecimal("200000.00"),
    deficit = BigDecimal("45000.00"),
    drift = BigDecimal("18.37"),
    unattributed = BigDecimal("50000.00"),
    classes = listOf(
        ClassAllocation(
            assetClass = AssetClass.ACOES,
            target = BigDecimal("50.00"),
            ideal = BigDecimal("122500.00"),
            actual = BigDecimal("120000.00"),
            deficit = BigDecimal("2500.00"),
            drift = BigDecimal("-1.02"),
            unattributed = BigDecimal("120000.00"),
            strategies = emptyList(),
            subClasses = emptyList(),
        ),
    ),
)

private val taxReport = StockTaxReport(
    period = period,
    meter = ExemptionMeter(
        grossSales = BigDecimal("10000.00"),
        exemptionLimit = BigDecimal("20000.00"),
        exceeded = false,
        realizedGain = BigDecimal("500.00"),
        taxableGain = BigDecimal.ZERO,
        rate = BigDecimal("15.00"),
        tax = BigDecimal.ZERO,
    ),
    averagePrices = emptyList(),
    realized = emptyList(),
    carry = listOf(
        CarryBucket(
            kind = RealizedKind.SWING,
            openingBalance = BigDecimal("100.00"),
            lossBanked = BigDecimal("50.00"),
            lossApplied = BigDecimal.ZERO,
            closingBalance = BigDecimal("150.00"),
        ),
    ),
    dayTrades = emptyList(),
)

private fun orderList(
    transfers: List<TransferProposal> = listOf(transferProposal(8, TransferStatus.APPROVED)),
) = OrderList(
    period = period,
    orders = listOf(
        SuggestedOrder(
            id = 3,
            ticker = "ITUB4",
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            suggestedPrice = BigDecimal("10"),
            executedQuantity = BigDecimal.ZERO,
            status = SuggestedOrderStatus.SUGGESTED,
            matchedTradeId = null,
        ),
    ),
    transfers = transfers,
    unplannedTrades = listOf(UnplannedTrade(4, "CURY3", TradeSide.SELL, BigDecimal("5"))),
)

private fun position(ticker: String) = Position(
    ticker = ticker,
    quantity = BigDecimal("40"),
    averagePrice = BigDecimal("10"),
)

private fun strategy(id: Int, name: String) = Strategy(
    id = id,
    name = name,
    assetClass = AssetClass.ACOES,
    kind = StrategyKind.REPORT_DRIVEN,
)

private val octoberIncome = Income(
    id = 1,
    ticker = "ITUB4",
    date = LocalDate.parse("2026-10-15"),
    type = IncomeType.DIVIDEND,
    grossAmount = BigDecimal("100.00"),
    retainedAmount = BigDecimal("15.00"),
    netAmount = BigDecimal("85.00"),
    taxNature = TaxNature.INCOME,
    provenance = Provenance.B3,
    status = IncomeStatus.RECEIVED,
)

private val septemberIncome = octoberIncome.copy(
    id = 2,
    ticker = "VALE3",
    date = LocalDate.parse("2026-09-15"),
)
