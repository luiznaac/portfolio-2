package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.model.FixedRateBond
import dev.agner.portfolio.usecase.bond.model.FloatingRateBond
import dev.agner.portfolio.usecase.bond.repository.IBondOrderYieldRepository
import dev.agner.portfolio.usecase.extension.runningFoldWithData
import dev.agner.portfolio.usecase.index.IndexValueService
import dev.agner.portfolio.usecase.index.model.IndexValue
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

@Service
class BondConsolidator(
    private val repository: IBondOrderYieldRepository, // TODO(): Create service and use that instead
    private val bondOrderService: BondOrderService,
    private val indexValueService: IndexValueService,
    private val calculator: BondCalculator,
) {

    // TODO(): Handle SELL orders
    // TODO(): Handle FixedRateBonds
    // TODO(): In the future, we'll need to handle multiple users, so this should take a userId as well
    suspend fun consolidateBy(bondId: Int) {
        val orders = bondOrderService.fetchByBondId(bondId).sortedBy { it.date }

        orders
            .filter { it.type == BondOrderType.BUY }
            .onEach { order ->
                val startingDate = order.resolveCalculationStartingDate()
                val yieldPercentages = order.bond.buildYieldPercentages(startingDate)
                    .associate { p -> p.date to p.percentage }

                yieldPercentages
                    .keys.sorted() // TODO(): Calculate dates programmatically - doing this now because we must get holidays right
                    .runningFoldWithData(order.initData(startingDate)) { data, date ->
                        val result = calculator.calculate(data.toConsolidationContext(yieldPercentages[date]!!))

                        result.toIntermediateData() to result.statements.map {
                            BondOrderStatementCreation(order.id, date, it.amount)
                        }
                    }
                    .flatten()
                    // TODO(): Move chunk logic to repository
                    .chunked(100)
                    .onEach { repository.saveAll(it) }
            }
    }

    private fun BondCalculationResult.toIntermediateData() = IntermediateData(principal, yield)

    private suspend fun BondOrder.resolveCalculationStartingDate() =
        repository.fetchLastByBondOrderId(id)?.date ?: date

    // TODO(): Extract to separate service
    private suspend fun Bond.buildYieldPercentages(startingAt: LocalDate) = when (this) {
        is FloatingRateBond -> indexValueService.fetchAllBy(indexId, startingAt).map { YieldPercentage(value, it) }
        is FixedRateBond -> TODO()
        else -> throw IllegalStateException("Unknown bond type: ${this::class.simpleName}")
    }

    private suspend fun BondOrder.initData(startingAt: LocalDate) =
        IntermediateData(
            amount,
            repository.sumYieldUntil(id, startingAt) ?: 0.0,
        )

    private data class IntermediateData(
        val principal: Double,
        val yieldAmount: Double,
    ) {
        fun toConsolidationContext(
            yieldPercentage: Double
        ) = BondCalculationContext(principal, yieldAmount, yieldPercentage)
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
