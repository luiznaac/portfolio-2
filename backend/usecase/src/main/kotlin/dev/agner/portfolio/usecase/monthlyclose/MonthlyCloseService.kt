package dev.agner.portfolio.usecase.monthlyclose

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.CorporateActionCalculator
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.income.IncomeService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.monthlyclose.model.AttributionSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.ClassTotalSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.HoldingSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.IncomeTotals
import dev.agner.portfolio.usecase.monthlyclose.model.OrdersSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
import dev.agner.portfolio.usecase.monthlyclose.model.StrategySliceSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.TaxCloseSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.TickerIncomeSnapshot
import dev.agner.portfolio.usecase.monthlyclose.repository.ISnapshotRepository
import dev.agner.portfolio.usecase.order.OrderLifecycleService
import dev.agner.portfolio.usecase.order.model.OrderList
import dev.agner.portfolio.usecase.order.model.TransferStatus
import dev.agner.portfolio.usecase.portfolio.AllocationService
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.Allocation
import dev.agner.portfolio.usecase.portfolio.model.ClassAllocation
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import dev.agner.portfolio.usecase.tax.StockTaxService
import dev.agner.portfolio.usecase.tax.model.StockTaxReport
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.springframework.stereotype.Service
import java.math.BigDecimal

// The monthly close: the ritual's last step. It refreshes the order plan first — which is what
// finalizes the attribution transfers (auto-approving the sub-threshold ones) — refuses while the
// refreshed inbox still holds a pending transfer, and otherwise locks the month and persists its
// immutable Snapshot in one transaction.
@Service
class MonthlyCloseService(
    private val months: MonthService,
    private val orders: OrderLifecycleService,
    private val allocations: AllocationService,
    private val positions: PositionService,
    private val income: IncomeService,
    private val tax: StockTaxService,
    private val attributions: IAttributionRepository,
    private val strategies: IStrategyRepository,
    private val corporateActions: ICorporateActionRepository,
    private val quotes: QuoteService,
    private val snapshots: ISnapshotRepository,
    private val transaction: ITransactionTemplate,
) {

    // Null when no month is open — the edge maps that to the 404 the current-month reads promise.
    suspend fun closeCurrent(): Snapshot? {
        val current = months.fetchCurrent() ?: return null

        // The refresh commits on its own: when the inbox still has a pending transfer, the 409 must
        // point at rows the caller can then approve by id.
        val refreshed = orders.refresh() ?: return null
        val pending = refreshed.transfers.filter { it.status == TransferStatus.PENDING }
        if (pending.isNotEmpty()) throw PendingTransfersException(pending)

        return transaction.execute { closeLocked(current) }
    }

    // Runs inside the close's transaction: re-reads the inbox under it so a concurrent refresh cannot
    // slip a PENDING proposal past the gate, then freezes the state the month is locked with.
    private suspend fun closeLocked(current: CurrentMonth): Snapshot? {
        // The month could have been replaced between the refresh and this transaction; the refreshed
        // plan belongs to the month that was open then, so a different month aborts the close.
        val open = months.fetchCurrent() ?: return null
        if (open.month.id != current.month.id) return null

        val persisted = orders.fetch() ?: return null
        val pending = persisted.transfers.filter { it.status == TransferStatus.PENDING }
        if (pending.isNotEmpty()) throw PendingTransfersException(pending)

        val allocation = allocations.fetchCurrent() ?: return null
        val report = tax.fetchCurrent() ?: return null
        val state = closeState(month = open.month, allocation = allocation, report = report, orderList = persisted)
        val closed = months.close(open.month.id) ?: return null

        return snapshots.save(closed.id, state)
    }

    // The closed months, newest period first — the read historical navigation is built on.
    suspend fun fetchClosedMonths(): List<Month> = months.fetchClosed()

    // One closed month's frozen state — the history screen's summary read. Null for a month that was
    // never closed; the edge maps that to the same 404 the month-scoped performance read uses.
    suspend fun fetchSnapshot(monthId: Int): Snapshot? = snapshots.fetchByMonth(monthId)

    // The allocation values the book at the latest stored close — the same valuation the order plan
    // uses — and the snapshot freezes that state at close time. Income is the month's movements by
    // payable date, whatever their lifecycle stage: the close records what the month earned, not only
    // what had already landed.
    private suspend fun closeState(
        month: Month,
        allocation: Allocation,
        report: StockTaxReport,
        orderList: OrderList,
    ): CloseState {
        val strategyCatalog = strategies.fetchAll().associateBy { it.id }
        val attributionLedger = attributions.fetchAll()
        val previous = previousClose(month.period)
        val slices = strategySlices(strategyCatalog, attributionLedger, previous, month.period)
        val attributedByClass = slices
            .groupBy { strategyCatalog.assetClassOf(it.strategyId) }
            .mapValues { (_, classSlices) -> classSlices.sumOf { it.valueEnd } }

        return CloseState(
            period = month.period,
            capital = month.capital.defaultScale(),
            floor = month.floor.defaultScale(),
            aportes = month.aportes.defaultScale(),
            caixa = (month.capital - allocation.actual).defaultScale(),
            holdings = holdingSnapshots(),
            attributions = attributionSnapshots(strategyCatalog, attributionLedger),
            classes = allocation.classes.map {
                it.toSnapshot(attributedByClass[it.assetClass] ?: BigDecimal.ZERO)
            },
            strategies = slices,
            income = incomeTotals(month.period),
            incomeByTicker = incomeByTicker(month.period),
            orders = OrdersSnapshot(
                orders = orderList.orders,
                transfers = orderList.transfers,
                unplannedTrades = orderList.unplannedTrades,
            ),
            tax = TaxCloseSnapshot(carry = report.carry, meter = report.meter),
        )
    }

    // The latest close before the month being closed — the prior book the strategy flow is measured
    // against. Null on the first close, where there is no prior book and therefore no measured flow.
    private suspend fun previousClose(period: YearMonth): CloseState? = snapshots.fetchAll()
        .filter { it.state.period < period }
        .maxByOrNull { it.state.period }
        ?.state

    // One slice per Strategy, the bucket included. `valueEnd` is the attributed units at the month's
    // close; `flow` is the net bookkeeping change since the prior close (a manual or suggested
    // transfer, the bulk legacy entry, a re-attribution), valued at the same close. A corporate
    // action's quantity scaling is not a bookkeeping change: the prior quantities are scaled by the
    // month's quantity events first, so a split does not read as money arriving. Trade-driven value
    // changes flow through the slice the way they flow through a class.
    private suspend fun strategySlices(
        strategyCatalog: StrategyCatalog,
        attributionLedger: List<Attribution>,
        previous: CloseState?,
        period: YearMonth,
    ): List<StrategySliceSnapshot> {
        val priorRows = previous?.attributions.orEmpty()
        val quantityEvents = quantityEventsBetween(from = previous?.period, to = period)
        val closeByTicker = (attributionLedger.map { it.ticker } + priorRows.map { it.ticker })
            .distinct()
            .associateWith { quotes.latestClose(it) ?: BigDecimal.ZERO }
        val rowsByStrategy = attributionLedger.groupBy { it.strategyId }
        val priorByStrategy = priorRows.groupBy { it.strategyId }

        return strategyCatalog.values
            .sortedBy { it.name }
            .map { strategy ->
                val rows = rowsByStrategy[strategy.id].orEmpty()
                StrategySliceSnapshot(
                    strategyId = strategy.id,
                    strategy = strategy.name,
                    kind = strategy.kind,
                    valueEnd = rows.sumOf { it.quantity * closeByTicker.getValue(it.ticker) }.defaultScale(),
                    flow = flowOf(
                        rows = rows,
                        prior = priorByStrategy[strategy.id].orEmpty(),
                        closeByTicker = closeByTicker,
                        quantityEvents = quantityEvents,
                        hasPrevious = previous != null,
                    ),
                )
            }
    }

    // The applied quantity events between the prior close and this one, per ticker, in replay order.
    // They scale both the custody and every attribution slice, so the flow must scale the prior
    // slice the same way before diffing it against the current one.
    private suspend fun quantityEventsBetween(
        from: YearMonth?,
        to: YearMonth,
    ): Map<String, List<CorporateAction>> {
        if (from == null) return emptyMap()

        val start = from.endDate()
        val end = to.endDate()
        return corporateActions.fetchApplied()
            .filter { it.kind.isQuantityEvent && it.exDate > start && it.exDate <= end }
            .sortedWith(compareBy({ it.exDate }, { it.ordinal }))
            .groupBy { it.ticker }
    }

    // The net bookkeeping flow over the union of the slice's current and prior tickers: a row the
    // transfer deleted at zero still carries its outflow, and a new row carries its inflow.
    private fun flowOf(
        rows: List<Attribution>,
        prior: List<AttributionSnapshot>,
        closeByTicker: Map<String, BigDecimal>,
        quantityEvents: Map<String, List<CorporateAction>>,
        hasPrevious: Boolean,
    ): BigDecimal {
        if (!hasPrevious) return BigDecimal.ZERO

        val currentByTicker = rows.associate { it.ticker to it.quantity }
        val priorByTicker = prior.associate { it.ticker to it.quantity }
        return (currentByTicker.keys + priorByTicker.keys)
            .sumOf { ticker ->
                val current = currentByTicker[ticker] ?: BigDecimal.ZERO
                val before = quantityEvents[ticker].orEmpty()
                    .fold(priorByTicker[ticker] ?: BigDecimal.ZERO) { quantity, action ->
                        CorporateActionCalculator.scaleQuantity(quantity, action)
                    }
                (current - before) * closeByTicker.getValue(ticker)
            }
            .defaultScale()
    }

    private suspend fun holdingSnapshots(): List<HoldingSnapshot> = positions.fetchAll()
        .sortedBy { it.ticker }
        .map { HoldingSnapshot(ticker = it.ticker, quantity = it.quantity, averagePrice = it.averagePrice) }

    private fun attributionSnapshots(
        strategyCatalog: StrategyCatalog,
        attributionLedger: List<Attribution>,
    ): List<AttributionSnapshot> = attributionLedger
        .map {
            AttributionSnapshot(
                ticker = it.ticker,
                strategyId = it.strategyId,
                strategy = strategyCatalog.nameOf(it.strategyId),
                quantity = it.quantity,
            )
        }
        .sortedWith(compareBy({ it.ticker }, { it.strategy }))

    private suspend fun incomeTotals(period: YearMonth): IncomeTotals {
        val ofMonth = incomeOfMonth(period)
        return IncomeTotals(
            gross = ofMonth.sumOf { it.grossAmount }.defaultScale(),
            retained = ofMonth.sumOf { it.retainedAmount }.defaultScale(),
            net = ofMonth.sumOf { it.netAmount }.defaultScale(),
        )
    }

    private suspend fun incomeByTicker(period: YearMonth): List<TickerIncomeSnapshot> =
        incomeOfMonth(period)
            .groupBy { it.ticker }
            .map { (ticker, rows) -> TickerIncomeSnapshot(ticker, rows.sumOf { it.netAmount }.defaultScale()) }
            .sortedBy { it.ticker }

    private suspend fun incomeOfMonth(period: YearMonth) =
        income.fetchAll().filter { it.date.year == period.year && it.date.month == period.month }
}

// The frozen class rung. A listed class's outside remainder is its value minus its frozen slices —
// the bucket is attributed, so it is not part of it. RENDA_FIXA has no Strategy, so its own figure
// stands (the fixed-income book is never "outside a Strategy").
private fun ClassAllocation.toSnapshot(attributed: BigDecimal): ClassTotalSnapshot =
    ClassTotalSnapshot(
        assetClass = assetClass,
        target = target,
        ideal = ideal,
        actual = actual,
        deficit = deficit,
        drift = drift,
        unattributed = if (assetClass == AssetClass.RENDA_FIXA) {
            unattributed
        } else {
            (actual - attributed).defaultScale()
        },
    )

// The strategy catalog keyed by id — the shape the close's slice, attribution and class passes share.
private typealias StrategyCatalog = Map<Int, Strategy>

private fun StrategyCatalog.nameOf(strategyId: Int) = this[strategyId]?.name ?: strategyId.toString()

private fun StrategyCatalog.assetClassOf(strategyId: Int) = this[strategyId]?.assetClass

private fun YearMonth.endDate(): LocalDate =
    LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
