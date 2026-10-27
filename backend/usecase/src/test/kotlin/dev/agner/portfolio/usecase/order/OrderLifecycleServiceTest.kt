package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.classTarget
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.order.model.AttributionTransfer
import dev.agner.portfolio.usecase.order.model.InvalidOrderMatchException
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.order.model.SuggestedOrder
import dev.agner.portfolio.usecase.order.model.SuggestedOrderCreation
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalCreation
import dev.agner.portfolio.usecase.order.model.TransferStatus
import dev.agner.portfolio.usecase.order.repository.ISuggestedOrderRepository
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import dev.agner.portfolio.usecase.strategyTarget
import dev.agner.portfolio.usecase.tickerTarget
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The lifecycle's orchestration seam: a refresh writes what the plan computed (matched against the
// month's ledger), replaces the pending transfer inbox and applies the auto-approved moves; reads
// never compute a new plan. Every write goes through the transaction seam.
class OrderLifecycleServiceTest : StringSpec({
    val months = mockk<MonthService>(relaxed = true)
    val plans = mockk<OrderPlanService>(relaxed = true)
    val trades = mockk<ITradeRepository>(relaxed = true)
    val suggestions = mockk<ISuggestedOrderRepository>(relaxed = true)
    val transfers = mockk<ITransferProposalRepository>(relaxed = true)
    val attributions = mockk<IAttributionRepository>(relaxed = true)
    val strategies = mockk<IStrategyRepository>(relaxed = true)
    val service = OrderLifecycleService(
        months = months,
        plans = plans,
        trades = trades,
        suggestions = suggestions,
        transfers = transfers,
        attributions = attributions,
        strategies = strategies,
        transaction = immediateTransactions,
    )

    beforeTest {
        clearAllMocks()
        coEvery { months.fetchCurrent() } returns currentMonth
        coEvery { trades.fetchAll() } returns emptyList()
        coEvery { transfers.fetchBy(any()) } returns emptyList()
        coEvery { strategies.fetchAll() } returns listOf(strategy(1, "Top"), strategy(2, "Small"))
        coEvery { suggestions.replaceAll(any(), any()) } returns emptyList()
    }

    "returns null when no month is open and writes nothing" {
        coEvery { months.fetchCurrent() } returns null

        service.refresh() shouldBe null
        service.fetch() shouldBe null

        coVerify(exactly = 0) { suggestions.replaceAll(any(), any()) }
    }

    "refresh persists the computed orders matched against the month's ledger" {
        coEvery { plans.computePlan(OrderPlanPhase.FULL) } returns plan(
            price = "10",
            capital = "1000",
            strategies = listOf("Top" to "100"),
            tickers = listOf("Top" to "100"),
            position = "30",
        )
        coEvery { trades.fetchAll() } returns listOf(
            trade(1, "ITUB4", TradeSide.BUY, "70"),
            trade(2, "CURY3", TradeSide.SELL, "40"),
            trade(3, "ITUB4", TradeSide.BUY, "70", date = "2026-09-05"),
        )
        val creations = slot<List<SuggestedOrderCreation>>()
        coEvery { suggestions.replaceAll(1, capture(creations)) } answers {
            creations.captured.mapIndexed { index, creation -> persisted(index + 1, creation) }
        }

        val list = service.refresh()!!

        list.period shouldBe YearMonth.parse("2026-10")
        list.orders.single().status shouldBe SuggestedOrderStatus.EXECUTED
        list.orders.single().executedQuantity.plain() shouldBe "70"
        list.unplannedTrades.map { it.ticker } shouldBe listOf("CURY3")

        val creation = creations.captured.single()
        creation.ticker shouldBe "ITUB4"
        creation.quantity.plain() shouldBe "70"
        creation.executedQuantity.plain() shouldBe "70"
        creation.matchedTradeId shouldBe 1
        creation.status shouldBe SuggestedOrderStatus.EXECUTED
        creation.suggestedPrice!!.plain() shouldBe "10"
    }

    "refresh auto-approves a transfer below the threshold and applies the attribution move" {
        coEvery { plans.computePlan(OrderPlanPhase.FULL) } returns plan(
            price = "10",
            capital = "1000",
            strategies = listOf("Top" to "50", "Small" to "50"),
            tickers = listOf("Top" to "100", "Small" to "100"),
            position = "70",
            attributions = mapOf("Top" to "10", "Small" to "60"),
            transfers = listOf(transfer(amount = "100")),
        )
        coEvery { attributions.fetchBy("ITUB4") } returns listOf(
            Attribution("ITUB4", 1, BigDecimal("10")),
            Attribution("ITUB4", 2, BigDecimal("60")),
        )
        val creations = slot<TransferProposalCreation>()
        coEvery { transfers.save(1, capture(creations)) } answers {
            persistedTransfer(1, creations.captured)
        }

        service.refresh()

        coVerify { transfers.deletePending(1) }
        val creation = creations.captured
        creation.ticker shouldBe "ITUB4"
        creation.fromStrategyId shouldBe 2
        creation.toStrategyId shouldBe 1
        creation.quantity.plain() shouldBe "10"
        creation.amount!!.plain() shouldBe "100"
        creation.autoApproved shouldBe true
        creation.status shouldBe TransferStatus.APPROVED

        // The approved move rebalances the ledger: Small gives 10 units, Top gains them.
        coVerify { attributions.save(Attribution("ITUB4", 2, BigDecimal("50"))) }
        coVerify { attributions.save(Attribution("ITUB4", 1, BigDecimal("20"))) }
    }

    "refresh never re-proposes a transfer the user rejected" {
        coEvery { plans.computePlan(OrderPlanPhase.FULL) } returns plan(
            price = "10",
            capital = "1000",
            strategies = listOf("Top" to "50", "Small" to "50"),
            tickers = listOf("Top" to "100", "Small" to "100"),
            position = "70",
            attributions = mapOf("Top" to "10", "Small" to "60"),
            transfers = listOf(transfer(amount = "100")),
        )
        coEvery { transfers.fetchBy(1) } returns listOf(
            persistedTransfer(
                1,
                TransferProposalCreation(
                    ticker = "ITUB4",
                    fromStrategyId = 2,
                    toStrategyId = 1,
                    quantity = BigDecimal("10"),
                    amount = BigDecimal("100"),
                    autoApproved = false,
                    status = TransferStatus.REJECTED,
                ),
            ),
        )

        service.refresh()

        coVerify(exactly = 0) { transfers.save(any(), any()) }
    }

    "fetch reports the persisted inbox and every trade no suggestion planned" {
        coEvery { suggestions.fetchBy(1) } returns listOf(
            persisted(
                1,
                SuggestedOrderCreation(
                    ticker = "ITUB4",
                    side = dev.agner.portfolio.usecase.order.model.OrderSide.BUY,
                    quantity = BigDecimal("70"),
                    suggestedPrice = BigDecimal("10"),
                    executedQuantity = BigDecimal("70"),
                    status = SuggestedOrderStatus.EXECUTED,
                    matchedTradeId = 1,
                ),
            ),
        )
        coEvery { trades.fetchAll() } returns listOf(
            trade(1, "ITUB4", TradeSide.BUY, "70"),
            trade(2, "CURY3", TradeSide.SELL, "40"),
        )

        val list = service.fetch()!!

        list.orders.single().ticker shouldBe "ITUB4"
        list.unplannedTrades.map { it.ticker } shouldBe listOf("CURY3")
    }

    "approving a pending transfer applies the move and records the decision" {
        coEvery { transfers.fetchById(7) } returns pendingTransfer(7)
        coEvery { transfers.decide(7, TransferStatus.APPROVED) } returns
            persistedTransfer(7, pendingCreation(status = TransferStatus.APPROVED))
        coEvery { attributions.fetchBy("ITUB4") } returns listOf(
            Attribution("ITUB4", 1, BigDecimal("10")),
            Attribution("ITUB4", 2, BigDecimal("60")),
        )

        val decided = service.approveTransfer(7)!!

        decided.status shouldBe TransferStatus.APPROVED
        coVerify { attributions.save(Attribution("ITUB4", 2, BigDecimal("50"))) }
        coVerify { attributions.save(Attribution("ITUB4", 1, BigDecimal("20"))) }
        coVerify { transfers.decide(7, TransferStatus.APPROVED) }
    }

    "rejecting a transfer records the decision without touching the ledger" {
        coEvery { transfers.fetchById(7) } returns pendingTransfer(7)
        coEvery { transfers.decide(7, TransferStatus.REJECTED) } returns
            persistedTransfer(7, pendingCreation(status = TransferStatus.REJECTED))

        service.rejectTransfer(7)!!.status shouldBe TransferStatus.REJECTED

        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "an override retargets the match at a trade of the same ticker and side" {
        coEvery { suggestions.fetchById(3) } returns persisted(3, buyCreation())
        coEvery { trades.fetchById(9) } returns
            trade(9, "ITUB4", TradeSide.BUY, "70", source = TradeSource.B3_IMPORT)
        coEvery { suggestions.updateMatch(3, 9, BigDecimal("70"), SuggestedOrderStatus.IMPORTED) } returns
            persisted(3, buyCreation(matchedTradeId = 9))

        service.overrideMatch(3, 9)!!.matchedTradeId shouldBe 9

        coVerify { suggestions.updateMatch(3, 9, BigDecimal("70"), SuggestedOrderStatus.IMPORTED) }
    }

    "an override to another ticker or side is rejected" {
        coEvery { suggestions.fetchById(3) } returns persisted(3, buyCreation())
        coEvery { trades.fetchById(9) } returns trade(9, "VALE3", TradeSide.BUY, "45")

        shouldThrow<InvalidOrderMatchException> { service.overrideMatch(3, 9) }

        coEvery { trades.fetchById(10) } returns trade(10, "ITUB4", TradeSide.SELL, "45")
        shouldThrow<InvalidOrderMatchException> { service.overrideMatch(3, 10) }
        coVerify(exactly = 0) { suggestions.updateMatch(any(), any(), any(), any()) }
    }

    "clearing an override returns the order to SUGGESTED" {
        coEvery { suggestions.fetchById(3) } returns persisted(3, buyCreation(matchedTradeId = 9))
        coEvery { suggestions.updateMatch(3, null, BigDecimal.ZERO, SuggestedOrderStatus.SUGGESTED) } returns
            persisted(3, buyCreation(status = SuggestedOrderStatus.SUGGESTED))

        service.overrideMatch(3, null)!!.status shouldBe SuggestedOrderStatus.SUGGESTED

        coVerify { suggestions.updateMatch(3, null, BigDecimal.ZERO, SuggestedOrderStatus.SUGGESTED) }
    }
})

// The transaction seam's contract is "run this now"; the test runs the block inline rather than
// mocking a generic suspend lambda.
private val immediateTransactions = object : ITransactionTemplate {
    override suspend fun <T> execute(block: suspend () -> T): T = block()
}

private val currentMonth = CurrentMonth(
    month = Month(
        id = 1,
        period = YearMonth.parse("2026-10"),
        capital = BigDecimal("1000"),
        floor = BigDecimal.ZERO,
        status = MonthStatus.OPEN,
    ),
    targets = Targets(classes = emptyList(), strategies = emptyList(), subClasses = emptyList(), tickers = emptyList()),
)

private fun plan(
    price: String,
    capital: String,
    strategies: List<Pair<String, String>>,
    tickers: List<Pair<String, String>>,
    position: String,
    attributions: Map<String, String> = emptyMap(),
    transfers: List<AttributionTransfer> = emptyList(),
): OrderPlan = OrderPlanCalculator.compute(
    OrderPlanContext(
        period = YearMonth.parse("2026-10"),
        phase = OrderPlanPhase.FUNDING,
        capital = BigDecimal(capital),
        floor = BigDecimal.ZERO,
        targets = Targets(
            classes = listOf(classTarget(AssetClass.ACOES, "100")),
            strategies = strategies.map { (strategy, percentage) ->
                strategyTarget(AssetClass.ACOES, strategy, percentage)
            },
            subClasses = emptyList(),
            tickers = tickers.map { (strategy, percentage) -> tickerTarget(strategy, "ITUB4", percentage) },
        ),
        positions = mapOf("ITUB4" to BigDecimal(position)),
        prices = mapOf("ITUB4" to BigDecimal(price)),
        attributionBySlice = attributions.map { (strategy, quantity) ->
            StrategyTicker(strategy, "ITUB4") to BigDecimal(quantity)
        }.toMap(),
    ),
).copy(transfers = transfers)

// The full plan's attribution transfer, as the planner emits it (both slices priced).
private fun transfer(amount: String) = AttributionTransfer(
    ticker = "ITUB4",
    fromStrategy = "Small",
    toStrategy = "Top",
    quantity = BigDecimal("10"),
    price = BigDecimal("10"),
    amount = BigDecimal(amount),
)

private fun strategy(id: Int, name: String) = Strategy(
    id = id,
    name = name,
    assetClass = AssetClass.ACOES,
    kind = StrategyKind.REPORT_DRIVEN,
)

private fun trade(
    id: Int,
    ticker: String,
    side: TradeSide,
    quantity: String,
    source: TradeSource = TradeSource.MANUAL,
    date: String = "2026-10-05",
): Trade = when (side) {
    TradeSide.BUY -> Trade.Buy(
        id = id,
        ticker = ticker,
        date = LocalDate.parse(date),
        venue = "B3",
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal("10"),
        institutionId = 1,
        fee = null,
        source = source,
        confirmedByB3 = false,
        ordinal = 1,
    )
    TradeSide.SELL -> Trade.Sell(
        id = id,
        ticker = ticker,
        date = LocalDate.parse(date),
        venue = "B3",
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal("10"),
        institutionId = 1,
        fee = null,
        source = source,
        confirmedByB3 = false,
        ordinal = 1,
    )
}

private fun persisted(id: Int, creation: SuggestedOrderCreation) = SuggestedOrder(
    id = id,
    ticker = creation.ticker,
    side = creation.side,
    quantity = creation.quantity,
    suggestedPrice = creation.suggestedPrice,
    executedQuantity = creation.executedQuantity,
    status = creation.status,
    matchedTradeId = creation.matchedTradeId,
)

private fun buyCreation(
    matchedTradeId: Int? = null,
    status: SuggestedOrderStatus = SuggestedOrderStatus.EXECUTED,
) = SuggestedOrderCreation(
    ticker = "ITUB4",
    side = dev.agner.portfolio.usecase.order.model.OrderSide.BUY,
    quantity = BigDecimal("70"),
    suggestedPrice = BigDecimal("10"),
    executedQuantity = BigDecimal("70"),
    status = status,
    matchedTradeId = matchedTradeId,
)

private fun pendingCreation(status: TransferStatus = TransferStatus.PENDING) = TransferProposalCreation(
    ticker = "ITUB4",
    fromStrategyId = 2,
    toStrategyId = 1,
    quantity = BigDecimal("10"),
    amount = BigDecimal("100"),
    autoApproved = false,
    status = status,
)

private fun pendingTransfer(id: Int) = persistedTransfer(id, pendingCreation())

private fun persistedTransfer(id: Int, creation: TransferProposalCreation) = TransferProposal(
    id = id,
    ticker = creation.ticker,
    fromStrategyId = creation.fromStrategyId,
    fromStrategy = "Small",
    toStrategyId = creation.toStrategyId,
    toStrategy = "Top",
    quantity = creation.quantity,
    amount = creation.amount,
    autoApproved = creation.autoApproved,
    status = creation.status,
)
