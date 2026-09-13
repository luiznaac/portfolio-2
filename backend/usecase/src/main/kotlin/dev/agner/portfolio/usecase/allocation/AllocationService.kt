package dev.agner.portfolio.usecase.allocation

import dev.agner.portfolio.usecase.allocation.classification.model.ProductClassification
import dev.agner.portfolio.usecase.allocation.model.AllocationPlan
import dev.agner.portfolio.usecase.allocation.model.AssetClassTargetCreation
import dev.agner.portfolio.usecase.allocation.model.CapitalSnapshotCreation
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClassTargetCreation
import dev.agner.portfolio.usecase.allocation.repository.ICapitalSnapshotRepository
import dev.agner.portfolio.usecase.commons.today
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

/**
 * Orchestrates the pieces RebalanceCalculator needs: latest capital, current targets, and each
 * tracked product's current value bucketed by AssetClass (and, for FIXED_INCOME, by
 * FixedIncomeSubClass). The calculator itself stays pure and untouched by any of this fetching.
 */
@Service
class AllocationService(
    private val capitalSnapshotRepository: ICapitalSnapshotRepository,
    private val targetsProvider: AllocationTargetsProvider,
    private val positionProvider: PositionProvider,
    private val calculator: RebalanceCalculator,
    private val clock: Clock,
) {

    suspend fun recordCapitalSnapshot(creation: CapitalSnapshotCreation) =
        capitalSnapshotRepository.save(creation)

    suspend fun fetchCapitalHistory() = capitalSnapshotRepository.fetchAll()

    suspend fun setClassTarget(creation: AssetClassTargetCreation) = targetsProvider.setClassTarget(creation)

    suspend fun fetchClassTargetHistory() = targetsProvider.fetchClassTargetHistory()

    suspend fun setFixedIncomeSubClassTarget(creation: FixedIncomeSubClassTargetCreation) =
        targetsProvider.setFixedIncomeSubClassTarget(creation)

    suspend fun fetchFixedIncomeSubClassTargetHistory() = targetsProvider.fetchFixedIncomeSubClassTargetHistory()

    suspend fun classify(classification: ProductClassification) = targetsProvider.classify(classification)

    suspend fun fetchClassifications() = targetsProvider.fetchClassifications()

    suspend fun currentPlan(): AllocationPlan {
        val today = LocalDate.today(clock)
        val capital = capitalSnapshotRepository.fetchLast()?.total ?: BigDecimal.ZERO
        val classTargets = targetsProvider.fetchCurrentClassTargets(today)
        val subClassTargets = targetsProvider.fetchCurrentSubClassTargets(today)
        val positions = positionProvider.fetchCurrentPositions()

        return calculator.calculate(capital, classTargets, subClassTargets, positions.byClass, positions.bySubClass)
    }
}
