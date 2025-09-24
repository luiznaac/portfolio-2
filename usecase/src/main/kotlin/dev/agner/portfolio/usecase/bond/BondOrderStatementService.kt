package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType.BUY
import dev.agner.portfolio.usecase.bond.model.FixedRateBond
import dev.agner.portfolio.usecase.bond.model.FloatingRateBond
import dev.agner.portfolio.usecase.bond.repository.IBondOrderYieldRepository
import dev.agner.portfolio.usecase.extension.mapToSet
import dev.agner.portfolio.usecase.index.IndexValueService
import dev.agner.portfolio.usecase.index.model.IndexValue
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

@Service
class BondOrderStatementService(
    private val repository: IBondOrderYieldRepository,
    private val bondOrderService: BondOrderService,
    private val indexValueService: IndexValueService,
) {

    // TODO(): Handle SELL orders
    // TODO(): Handle FixedRateBonds
    // TODO(): In the future, we'll need to handle multiple users, so this should take a userId as well
    suspend fun consolidateBy(bondId: Int) {
        val orders = bondOrderService.fetchByBondId(bondId)

        orders.first().bond
            .buildYieldPercentages(orders.resolveCalculationStartingDate())
            .sortedBy { it.date }
            .fold(emptySet<Consolidation>()) { consolidations, yieldPercentage ->
                val newConsolidations = orders.createNewConsolidations(yieldPercentage, consolidations)

                (consolidations + newConsolidations).calculateStatementsAndUpdateYield(yieldPercentage)
            }
            .flatMap { consolidation ->
                consolidation.statements.map {
                    BondOrderStatementCreation(consolidation.bondOrderId, it.date, it.amount)
                }
            }
            // TODO(): Move chunk logic to repository
            .chunked(100)
            .onEach { repository.saveAll(it) }
    }

    /**
     * Create new consolidations for BUY orders that do not have an existing consolidation yet.
     * We are iterating over the calculated yield percentage dates, so we need to get the newer orders as we
     * go down the date list and start to calculate the yield for them as well.
     */
    // TODO(): Extract to separate service
    private suspend fun List<BondOrder>.createNewConsolidations(
        yieldPercentage: YieldPercentage,
        existingConsolidations: Set<Consolidation>
    ): List<Consolidation> {
        val existingBondOrderIds = existingConsolidations.mapToSet { it.bondOrderId }

        return filter { it.date <= yieldPercentage.date && it.type == BUY }
            .filterNot { order -> order.id in existingBondOrderIds }
            .map { Consolidation(it, repository.sumYieldUntil(it.id, yieldPercentage.date)) }
    }

    private fun Set<Consolidation>.calculateStatementsAndUpdateYield(yieldPercentage: YieldPercentage) =
        mapToSet {
            val yieldAmount = (it.principal + it.yield) * yieldPercentage.percentage / 100
            it.copy(
                yield = it.yield + yieldAmount,
                statements = it.statements + StatementRecord(yieldPercentage.date, yieldAmount),
            )
        }

    // TODO(): Extract to separate service
    private suspend fun List<BondOrder>.resolveCalculationStartingDate() =
        minOf { repository.fetchLastByBondOrderId(it.id)?.date ?: it.date }

    // TODO(): Extract to separate service
    private suspend fun Bond.buildYieldPercentages(startingAt: LocalDate) = when (this) {
        is FloatingRateBond -> indexValueService.fetchAllBy(indexId, startingAt).map { YieldPercentage(value, it) }
        is FixedRateBond -> TODO()
        else -> throw IllegalStateException("Unknown bond type: ${this::class.simpleName}")
    }
}

private data class YieldPercentage(
    val date: LocalDate,
    val percentage: Double,
) {
    constructor(multiplier: Double, indexValue: IndexValue) : this(
        date = indexValue.date,
        percentage = (multiplier / 100) * indexValue.value,
    )
}

private data class Consolidation(
    val bondOrderId: Int,
    val principal: Double,
    val yield: Double,
    val statements: List<StatementRecord>,
) {
    constructor(order: BondOrder, startingYield: Double?) : this(
        bondOrderId = order.id,
        principal = order.amount,
        yield = startingYield ?: 0.0,
        statements = emptyList(),
    )
}

private data class StatementRecord(
    val date: LocalDate,
    val amount: Double,
)
