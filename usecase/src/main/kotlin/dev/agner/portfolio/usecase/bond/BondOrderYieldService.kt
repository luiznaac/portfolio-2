package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderYieldCreation
import dev.agner.portfolio.usecase.bond.model.FixedRateBond
import dev.agner.portfolio.usecase.bond.model.FloatingRateBond
import dev.agner.portfolio.usecase.bond.repository.IBondOrderYieldRepository
import dev.agner.portfolio.usecase.index.IndexValueService
import dev.agner.portfolio.usecase.index.model.IndexValue
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

@Service
class BondOrderYieldService(
    private val repository: IBondOrderYieldRepository,
    private val bondOrderService: BondOrderService,
    private val indexValueService: IndexValueService,
) {

    // TODO(): Handle SELL orders
    // TODO(): Handle FixedRateBonds
    // TODO(): In the future, we'll need to handle multiple users, so this should probably take a userId as well
    suspend fun consolidateBy(bondId: Int) {
        bondOrderService.fetchByBondId(bondId).onEach { order ->
            order
                .buildYieldPercentages()
                .sortedBy { it.date }
                .runningFold(UpdatedAmount(order.date, order.amount, 0.0)) { updatedAmount, yieldPercentage ->
                    val yieldAmount = updatedAmount.amount * yieldPercentage.percentage / 100
                    UpdatedAmount(
                        date = yieldPercentage.date,
                        amount = updatedAmount.amount + yieldAmount,
                        yieldAmount = yieldAmount,
                    )
                }
                .drop(1)
                .map { BondOrderYieldCreation(it.date, it.yieldAmount) }
                .chunked(100)
                .onEach { repository.saveAll(order.id, it) }
        }
    }

    private suspend fun BondOrder.buildYieldPercentages() = when (bond) {
        is FloatingRateBond -> indexValueService.fetchAllBy(bond.indexId, date).map { YieldPercentage(bond.value, it) }
        is FixedRateBond -> TODO()
        else -> throw IllegalStateException("Unknown bond type: ${bond::class}")
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

private data class UpdatedAmount(
    val date: LocalDate,
    val amount: Double,
    val yieldAmount: Double,
)
