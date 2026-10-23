package dev.agner.portfolio.usecase.monthlyclose

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.income.IncomeService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.monthlyclose.model.AttributionSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.ClassTotalSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.HoldingSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.IncomeTotals
import dev.agner.portfolio.usecase.monthlyclose.model.OrdersSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
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
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import dev.agner.portfolio.usecase.tax.StockTaxService
import dev.agner.portfolio.usecase.tax.model.StockTaxReport
import kotlinx.datetime.YearMonth
import org.springframework.stereotype.Service

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

    // The allocation values the book at the latest stored close — the same valuation the order plan
    // uses — and the snapshot freezes that state at close time. Income is the month's movements by
    // payable date, whatever their lifecycle stage: the close records what the month earned, not only
    // what had already landed.
    private suspend fun closeState(
        month: Month,
        allocation: Allocation,
        report: StockTaxReport,
        orderList: OrderList,
    ) = CloseState(
        period = month.period,
        capital = month.capital.defaultScale(),
        floor = month.floor.defaultScale(),
        aportes = month.aportes.defaultScale(),
        caixa = (month.capital - allocation.actual).defaultScale(),
        holdings = holdingSnapshots(),
        attributions = attributionSnapshots(),
        classes = allocation.classes.map { it.toSnapshot() },
        income = incomeTotals(month.period),
        incomeByTicker = incomeByTicker(month.period),
        orders = OrdersSnapshot(
            orders = orderList.orders,
            transfers = orderList.transfers,
            unplannedTrades = orderList.unplannedTrades,
        ),
        tax = TaxCloseSnapshot(carry = report.carry, meter = report.meter),
    )

    private suspend fun holdingSnapshots(): List<HoldingSnapshot> = positions.fetchAll()
        .sortedBy { it.ticker }
        .map { HoldingSnapshot(ticker = it.ticker, quantity = it.quantity, averagePrice = it.averagePrice) }

    private suspend fun attributionSnapshots(): List<AttributionSnapshot> {
        val names = strategies.fetchAll().associate { it.id to it.name }
        return attributions.fetchAll()
            .map {
                AttributionSnapshot(
                    ticker = it.ticker,
                    strategyId = it.strategyId,
                    strategy = names[it.strategyId] ?: it.strategyId.toString(),
                    quantity = it.quantity,
                )
            }
            .sortedWith(compareBy({ it.ticker }, { it.strategy }))
    }

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

private fun ClassAllocation.toSnapshot() = ClassTotalSnapshot(
    assetClass = assetClass,
    target = target,
    ideal = ideal,
    actual = actual,
    deficit = deficit,
    drift = drift,
    unattributed = unattributed,
)
