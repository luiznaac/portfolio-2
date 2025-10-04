package dev.agner.portfolio.usecase.bond.consolidation.model

import dev.agner.portfolio.usecase.index.model.IndexValue
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class BondConsolidationContext(
    val bondOrderId: Int,
    val contributionDate: LocalDate,
    val principal: BigDecimal,
    val yieldAmount: BigDecimal,
    val yieldPercentages: Map<LocalDate, YieldPercentageContext>,
    val sellOrders: Map<LocalDate, SellOrderContext> = emptyMap(),
) {
    data class SellOrderContext(
        val id: Int,
        val amount: BigDecimal,
    )

    data class YieldPercentageContext(
        val percentage: BigDecimal,
    ) {
        constructor(multiplier: BigDecimal, indexValue: IndexValue) : this(
            percentage = (multiplier / BigDecimal("100")) * indexValue.value,
        )
    }
}
