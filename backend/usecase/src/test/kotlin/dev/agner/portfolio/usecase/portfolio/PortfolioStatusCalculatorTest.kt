package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.ClassificationQueueItem
import dev.agner.portfolio.usecase.catalog.model.UnclassifiedReason
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportDestination
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.IncomeTotals
import dev.agner.portfolio.usecase.monthlyclose.model.OrdersSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
import dev.agner.portfolio.usecase.monthlyclose.model.TaxCloseSnapshot
import dev.agner.portfolio.usecase.order.model.OrderList
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.SuggestedOrder
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferStatus
import dev.agner.portfolio.usecase.order.model.UnplannedTrade
import dev.agner.portfolio.usecase.portfolio.model.Allocation
import dev.agner.portfolio.usecase.portfolio.model.AttentionKind
import dev.agner.portfolio.usecase.portfolio.model.AttentionObject
import dev.agner.portfolio.usecase.portfolio.model.AttentionSeverity
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthActionKind
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.PortfolioStatusInput
import dev.agner.portfolio.usecase.portfolio.model.StatusRefType
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.UnclassifiedAllocation
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.strategy.model.EditionDetail
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.EditionStatus
import dev.agner.portfolio.usecase.tax.model.CarryBucket
import dev.agner.portfolio.usecase.tax.model.ExemptionMeter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The status read's composition rules live in a pure calculator: the service gathers the book reads,
// this class turns them into the landing's six-step month block, its data-state block and the typed
// attention queue. Every rule is asserted here without a repository or a server.
class PortfolioStatusCalculatorTest : StringSpec({

    val asOf = LocalDate.parse("2026-11-12")

    "composes the top-level figures from the open month and the allocation" {
        val current = month(capital = "10000")
        val status = statusFor(
            asOf = asOf,
            current = current,
            allocation = allocation(capital = "10000", actual = "6000", deficit = "4000", drift = "-40"),
            closedMonths = listOf(month(period = "2026-10", status = MonthStatus.CLOSED).month),
        )

        status.asOf shouldBe asOf
        status.openMonth?.id shouldBe current.month.id
        status.openMonth?.period shouldBe current.month.period
        status.lastClose?.period shouldBe YearMonth.parse("2026-10")
        status.capital.toPlainString() shouldBe "10000"
        status.invested.toPlainString() shouldBe "6000"
        status.uninvestedBalance?.toPlainString() shouldBe "4000.00"
        status.deficit.toPlainString() shouldBe "4000"
        status.drift.toPlainString() shouldBe "-40"
    }

    "counts the six plan steps and names the next unfinished action" {
        val status = statusFor(
            current = month(capital = "10000"),
            allocation = allocation(),
            orders = orderList(
                orders = listOf(order(status = SuggestedOrderStatus.EXECUTED)),
                transfers = listOf(transfer(status = TransferStatus.PENDING)),
            ),
        )

        status.month.open shouldBe true
        status.month.stepsTotal shouldBe 6
        status.month.stepsDone shouldBe 4
        status.month.suggestedOrders shouldBe 1
        status.month.pendingTransfers shouldBe 1
        status.month.nextAction?.kind shouldBe MonthActionKind.DECIDE_TRANSFER
        status.month.nextAction?.ref?.type shouldBe StatusRefType.TRANSFER
        status.month.nextAction?.ref?.id shouldBe 7
    }

    "names the plan refresh as the next action before any plan is persisted" {
        val status = statusFor(
            current = month(capital = "10000"),
            allocation = allocation(),
            orders = null,
        )

        status.month.stepsDone shouldBe 2
        status.month.nextAction?.kind shouldBe MonthActionKind.REFRESH_PLAN
    }

    "does not count an unplanned trade as a calculated plan" {
        val status = statusFor(
            current = month(capital = "10000"),
            allocation = allocation(),
            orders = OrderList(
                period = YearMonth.parse("2026-11"),
                orders = emptyList(),
                transfers = emptyList(),
                unplannedTrades = listOf(
                    UnplannedTrade(
                        id = 9,
                        ticker = "ZZZZ9",
                        side = TradeSide.BUY,
                        quantity = BigDecimal("10"),
                    ),
                ),
            ),
        )

        status.month.stepsDone shouldBe 2
        status.month.nextAction?.kind shouldBe MonthActionKind.REFRESH_PLAN
    }

    "opens a month when none is open" {
        val status = statusFor(current = null, allocation = allocation(), orders = null)

        status.openMonth shouldBe null
        status.month.open shouldBe false
        status.month.stepsDone shouldBe 0
        status.month.nextAction?.kind shouldBe MonthActionKind.OPEN_MONTH
        status.month.nextAction?.ref?.type shouldBe StatusRefType.PORTFOLIO
        status.uninvestedBalance shouldBe null
    }

    "raises every actionable finding with a typed ref and numeric params" {
        val status = statusFor(
            current = month(capital = "10000"),
            allocation = allocation(),
            orders = orderList(transfers = listOf(transfer(status = TransferStatus.PENDING))),
            positions = listOf(Position("PETR4", BigDecimal("10"), BigDecimal("10"))),
            latestQuotes = mapOf("PETR4" to quote("2026-10-01", "48.92")),
            openGaps = listOf(gap()),
            pendingImports = listOf(import(status = ImportStatus.STAGED)),
            stagedEditions = listOf(edition()),
            unclassifiedTickers = listOf(
                ClassificationQueueItem("ZZZZ9", "ZZZZ9", BigDecimal("5"), UnclassifiedReason.UNHANDLED_BDI_CODE),
            ),
            reviewActions = listOf(reviewAction()),
        )

        status.attention.map { it.kind } shouldBe listOf(
            AttentionKind.PENDING_TRANSFER,
            AttentionKind.STAGED_IMPORT,
            AttentionKind.STAGED_EDITION,
            AttentionKind.CORPORATE_ACTION_REVIEW,
            AttentionKind.UNCLASSIFIED_TICKER,
            AttentionKind.OPEN_GAP,
            AttentionKind.STALE_QUOTE,
        )
        status.attention.map { it.severity } shouldBe listOf(
            AttentionSeverity.ACTION,
            AttentionSeverity.ACTION,
            AttentionSeverity.ACTION,
            AttentionSeverity.ACTION,
            AttentionSeverity.ACTION,
            AttentionSeverity.ACTION,
            AttentionSeverity.WATCH,
        )
        status.attention.map { it.`object` } shouldBe listOf(
            AttentionObject.MONTH,
            AttentionObject.IMPORT,
            AttentionObject.STRATEGY,
            AttentionObject.CORPORATE_ACTION,
            AttentionObject.ASSET,
            AttentionObject.ASSET,
            AttentionObject.ASSET,
        )

        val transfer = status.attention.first()
        transfer.ref.type shouldBe StatusRefType.TRANSFER
        transfer.ref.id shouldBe 7
        transfer.params.getValue("quantity").toPlainString() shouldBe "50"
        transfer.params.getValue("amount").toPlainString() shouldBe "500"

        val gap = status.attention.first { it.kind == AttentionKind.OPEN_GAP }
        gap.ref.type shouldBe StatusRefType.GAP
        gap.ref.id shouldBe 3
        gap.params.getValue("quantity").toPlainString() shouldBe "5"
        gap.params.getValue("age_days").toPlainString() shouldBe "42"

        val stale = status.attention.last()
        stale.ref.type shouldBe StatusRefType.QUOTE
        stale.ref.ticker shouldBe "PETR4"
        stale.params.getValue("days_old").toPlainString() shouldBe "42"
        stale.params.getValue("close").toPlainString() shouldBe "48.92"
    }

    "reports no attention for a clean book" {
        val status = statusFor(current = month(capital = "10000"), allocation = allocation())

        status.attention shouldBe emptyList()
    }

    "reports quote freshness, open gaps and the last snapshot in the data block" {
        val snapshot = snapshot()
        val status = statusFor(
            current = month(capital = "10000"),
            allocation = allocation(),
            positions = listOf(
                Position("PETR4", BigDecimal("10"), BigDecimal("10")),
                Position("VALE3", BigDecimal("5"), BigDecimal("10")),
            ),
            latestQuotes = mapOf(
                "PETR4" to quote("2026-10-01", "48.92"),
                "VALE3" to quote("2026-11-11", "61.10"),
            ),
            openGaps = listOf(gap()),
            lastSnapshot = snapshot,
            closedMonths = listOf(month(period = "2026-10", status = MonthStatus.CLOSED).month),
        )

        status.data.quotesAsOf shouldBe LocalDate.parse("2026-11-11")
        status.data.openGaps shouldBe 1
        status.data.staleQuotes.map { it.ticker } shouldBe listOf("PETR4")
        status.data.staleQuotes.first().daysOld shouldBe 42
        status.data.lastSnapshot?.monthId shouldBe snapshot.monthId
        status.data.lastSnapshot?.period shouldBe YearMonth.parse("2026-10")
    }
})

private fun statusFor(
    asOf: LocalDate = LocalDate.parse("2026-11-12"),
    current: CurrentMonth? = month(capital = "10000"),
    allocation: Allocation = allocation(),
    orders: OrderList? = orderList(),
    closedMonths: List<Month> = emptyList(),
    lastSnapshot: Snapshot? = null,
    positions: List<Position> = emptyList(),
    latestQuotes: Map<String, Quote> = emptyMap(),
    openGaps: List<Gap> = emptyList(),
    pendingImports: List<Import> = emptyList(),
    stagedEditions: List<EditionDetail> = emptyList(),
    unclassifiedTickers: List<ClassificationQueueItem> = emptyList(),
    reviewActions: List<CorporateAction> = emptyList(),
) = PortfolioStatusCalculator.compute(
    PortfolioStatusInput(
        asOf = asOf,
        current = current,
        allocation = allocation,
        orders = orders,
        closedMonths = closedMonths,
        lastSnapshot = lastSnapshot,
        positions = positions,
        latestQuotes = latestQuotes,
        openGaps = openGaps,
        pendingImports = pendingImports,
        stagedEditions = stagedEditions,
        unclassifiedTickers = unclassifiedTickers,
        reviewActions = reviewActions,
    ),
)

private fun month(
    id: Int = 1,
    period: String = "2026-11",
    capital: String = "10000",
    status: MonthStatus = MonthStatus.OPEN,
) = CurrentMonth(
    month = Month(
        id = id,
        period = YearMonth.parse(period),
        capital = BigDecimal(capital),
        floor = BigDecimal.ZERO,
        status = status,
    ),
    targets = Targets(
        classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("100"))),
        strategies = emptyList(),
        subClasses = emptyList(),
        tickers = emptyList(),
    ),
)

private fun allocation(
    capital: String = "10000",
    actual: String = "6000",
    deficit: String = "4000",
    drift: String = "-40",
) = Allocation(
    period = YearMonth.parse("2026-11"),
    capital = BigDecimal(capital),
    floor = BigDecimal.ZERO,
    ideal = BigDecimal(capital),
    actual = BigDecimal(actual),
    deficit = BigDecimal(deficit),
    drift = BigDecimal(drift),
    unattributed = BigDecimal.ZERO,
    unclassified = UnclassifiedAllocation(BigDecimal.ZERO, emptyList()),
    classes = emptyList(),
)

private fun orderList(
    orders: List<SuggestedOrder> = emptyList(),
    transfers: List<TransferProposal> = emptyList(),
) = OrderList(
    period = YearMonth.parse("2026-11"),
    orders = orders,
    transfers = transfers,
    unplannedTrades = emptyList(),
)

private fun order(status: SuggestedOrderStatus) = SuggestedOrder(
    id = 1,
    ticker = "PETR4",
    side = OrderSide.BUY,
    quantity = BigDecimal("10"),
    suggestedPrice = BigDecimal("10"),
    executedQuantity = if (status == SuggestedOrderStatus.SUGGESTED) BigDecimal.ZERO else BigDecimal("10"),
    status = status,
    matchedTradeId = null,
)

private fun transfer(status: TransferStatus) = TransferProposal(
    id = 7,
    ticker = "ITUB4",
    fromStrategyId = 2,
    fromStrategy = "Small Caps",
    toStrategyId = 3,
    toStrategy = "Top Dividendos",
    quantity = BigDecimal("50"),
    amount = BigDecimal("500"),
    autoApproved = false,
    status = status,
)

private fun quote(date: String, close: String) = Quote(
    ticker = "PETR4",
    date = LocalDate.parse(date),
    close = BigDecimal(close),
    source = QuoteSource.COTAHIST,
)

private fun gap() = Gap.Buy(
    id = 3,
    ticker = "VALE3",
    date = LocalDate.parse("2026-10-01"),
    quantity = BigDecimal("5"),
    unitPrice = BigDecimal("61.10"),
    status = GapStatus.OPEN,
)

private fun import(status: ImportStatus) = Import(
    id = 11,
    source = ImportSource.B3_MOVEMENTS,
    fileName = "movimentacoes.xlsx",
    fileHash = "abc",
    status = status,
    destination = ImportDestination(ImportDestinationObject.TRADES),
    hasOriginal = false,
    createdAt = LocalDateTime.parse("2026-11-10T10:00:00"),
)

private fun edition() = EditionDetail(
    id = 21,
    strategyId = 3,
    referenceMonth = YearMonth.parse("2026-11"),
    status = EditionStatus.STAGED,
    diffFromPrevious = null,
    entries = listOf(EditionEntry("PETR4", BigDecimal("100"))),
)

private fun reviewAction() = CorporateAction(
    id = 31,
    ticker = "VALE3",
    kind = CorporateActionKind.RIGHTS_ISSUE,
    exDate = LocalDate.parse("2026-10-20"),
    approvalDate = null,
    ratio = null,
    declaredValuePerShare = null,
    targetTicker = null,
    source = CorporateActionSource.B3_MOVEMENTS,
    processNumber = null,
    rawType = null,
    ordinal = 1,
    status = CorporateActionStatus.NEEDS_REVIEW,
)

private fun snapshot() = Snapshot(
    id = 41,
    monthId = 1,
    state = CloseState(
        period = YearMonth.parse("2026-10"),
        capital = BigDecimal("10000"),
        floor = BigDecimal.ZERO,
        caixa = BigDecimal.ZERO,
        holdings = emptyList(),
        attributions = emptyList(),
        classes = emptyList(),
        income = IncomeTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
        orders = OrdersSnapshot(emptyList(), emptyList(), emptyList()),
        tax = TaxCloseSnapshot(
            carry = emptyList<CarryBucket>(),
            meter = ExemptionMeter(
                grossSales = BigDecimal.ZERO,
                exemptionLimit = BigDecimal("20000"),
                exceeded = false,
                realizedGain = BigDecimal.ZERO,
                taxableGain = BigDecimal.ZERO,
                rate = BigDecimal("15"),
                tax = BigDecimal.ZERO,
            ),
        ),
    ),
    createdAt = LocalDateTime.parse("2026-10-31T18:00:00"),
)
