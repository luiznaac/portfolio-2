package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.MonthUpdate
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.repository.IMonthRepository
import dev.agner.portfolio.usecase.portfolio.repository.ITargetRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class MonthService(
    private val monthRepository: IMonthRepository,
    private val targetRepository: ITargetRepository,
) {

    // Null when no month is open — the edge maps that to the 404 that GET /month/current promises.
    suspend fun fetchCurrent(): CurrentMonth? = monthRepository.fetchOpen()?.let { view(it) }

    // Opening closes any month still open (the repository owns that invariant) and returns the new
    // month with its — still empty — target chain. A period that already has a month is refused.
    suspend fun open(creation: MonthCreation): CurrentMonth {
        if (monthRepository.fetchByPeriod(creation.period) != null) {
            throw MonthPeriodTakenException(creation.period)
        }
        return view(monthRepository.open(creation))
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
