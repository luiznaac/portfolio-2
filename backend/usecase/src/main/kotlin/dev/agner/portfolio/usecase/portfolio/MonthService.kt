package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.MonthPeriodRange
import dev.agner.portfolio.usecase.portfolio.model.MonthUpdate
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.repository.IMonthRepository
import dev.agner.portfolio.usecase.portfolio.repository.ITargetRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

@Service
class MonthService(
    private val monthRepository: IMonthRepository,
    private val targetRepository: ITargetRepository,
    private val trades: ITradeRepository,
    private val clock: Clock,
) {

    // Null when no month is open — the edge maps that to the 404 that GET /month/current promises.
    suspend fun fetchCurrent(): CurrentMonth? = monthRepository.fetchOpen()?.let { view(it) }

    // The window a month may be planned in, derived from the ledger, the existing months and the
    // server clock in one read. The opener, the opener's validation and the tax period picker all
    // read this same rule.
    suspend fun fetchPeriodRange(): MonthPeriodRange {
        val current = LocalDate.today(clock).yearMonth
        val months = monthRepository.fetchAll().map { it.period }
        val executionRange = trades.fetchExecutionDateRange()
        val earliestTrade = executionRange?.first?.yearMonth
        val latestTrade = executionRange?.last?.yearMonth
        val max = maxOf(current, months.maxOrNull() ?: current)
        val min = minOf(earliestTrade ?: max, months.minOrNull() ?: max, max)
        return MonthPeriodRange(
            min = min,
            max = max,
            default = (latestTrade ?: max).coerceIn(min, max),
        )
    }

    // Opening closes (or discards, when backdated) any month still open — the repository owns that
    // invariant — and returns the new month with its — still empty — target chain. A period that
    // already has a month is refused, and so is one outside the window.
    suspend fun open(creation: MonthCreation): CurrentMonth {
        if (monthRepository.fetchByPeriod(creation.period) != null) {
            throw MonthPeriodTakenException(creation.period)
        }
        val range = fetchPeriodRange()
        if (!range.contains(creation.period)) {
            throw MonthPeriodOutsideWindowException(period = creation.period, range = range)
        }
        return view(monthRepository.open(creation))
    }

    // A close freezes an immutable Snapshot valued at the latest close, so only the calendar month
    // the server is in may be closed; a backdated plan-only month is refused before any write.
    fun requireClosable(month: Month) {
        if (month.period < LocalDate.today(clock).yearMonth) throw MonthCloseNotCurrentException(month.period)
    }

    suspend fun updateCurrent(update: MonthUpdate): CurrentMonth? =
        monthRepository.updateCurrent(update)?.let { view(it) }

    // Locks the open month and returns it; null when it is not open (or already closed).
    suspend fun close(id: Int): Month? = monthRepository.close(id)

    suspend fun fetchClosed(): List<Month> = monthRepository.fetchClosed()

    suspend fun fetchCurrentTargets(): Targets? =
        monthRepository.fetchOpen()?.let { targetRepository.fetchByMonth(it.id) }

    // Null when no month is open. Rejects a class whose report shares over-plan it, replaces the
    // chain (normalising its tickers) and returns the refreshed current month.
    suspend fun replaceCurrentTargets(targets: Targets): CurrentMonth? =
        monthRepository.fetchOpen()?.let { month ->
            targets.rejectOverplannedClasses()
            targetRepository.replaceAll(month.id, targets.normalized())
            view(month)
        }

    private suspend fun view(month: Month) = CurrentMonth(month, targetRepository.fetchByMonth(month.id))
}

private val STRATEGY_SHARE_LIMIT = BigDecimal("100")

// Strategy Targets are shares of their class. A class may be targeted in full — 100 leaves the
// bucket's custody as the shortfall the plan offers to transfer — but never above it. Class Targets
// and ticker weights are not checked here.
private fun Targets.rejectOverplannedClasses() {
    val sharesByClass = strategies.groupBy { it.assetClass }
        .mapValues { (_, rows) -> rows.sumOf { it.percentage } }
    for ((assetClass, percentage) in sharesByClass) {
        if (percentage > STRATEGY_SHARE_LIMIT) throw StrategyTargetsExceedClassException(assetClass, percentage)
    }
}
