package dev.agner.portfolio.usecase.bond.consolidation.model

import dev.agner.portfolio.usecase.index.model.IndexValue
import kotlinx.datetime.LocalDate

data class BondConsolidationContext(
    val bondOrderId: Int,
    val principal: Double,
    val yieldAmount: Double,
    val yieldPercentages: Map<LocalDate, YieldPercentageContext>,
    val sellOrders: Map<LocalDate, SellOrderContext> = emptyMap(),
) {
    data class SellOrderContext(
        val id: Int,
        val amount: Double,
    )

    data class YieldPercentageContext(
        val percentage: Double,
    ) {
        constructor(multiplier: Double, indexValue: IndexValue) : this(
            percentage = (multiplier / 100) * indexValue.value,
        )
    }
}
