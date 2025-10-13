package dev.agner.portfolio.usecase.bond.consolidation.model

import dev.agner.portfolio.usecase.index.model.IndexValue
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class BondConsolidationContext(
    val bondOrderId: Int,
    val contributionDate: LocalDate,
    val dateRange: List<LocalDate>,
    val principal: BigDecimal,
    val yieldAmount: BigDecimal,
    val yieldPercentages: Map<LocalDate, YieldPercentageContext>,
    val redemptionOrders: Map<LocalDate, RedemptionContext> = emptyMap(),
    val downToZeroContext: DownToZeroContext? = null,
) {
    sealed class RedemptionContext(
        open val id: Int,
        open val amount: BigDecimal,
    ) {
        abstract fun copy(amount: BigDecimal): RedemptionContext

        data class SellContext(
            override val id: Int,
            override val amount: BigDecimal,
        ) : RedemptionContext(id, amount) {
            override fun copy(amount: BigDecimal): SellContext = copy(id = id, amount = amount)
        }
    }

    data class DownToZeroContext(
        val id: Int,
        val date: LocalDate,
    )

    data class YieldPercentageContext(
        val percentage: BigDecimal,
    ) {
        constructor(multiplier: BigDecimal, indexValue: IndexValue) : this(
            percentage = (multiplier / BigDecimal("100")) * indexValue.value,
        )
    }
}

data class BondMaturityConsolidationContext(
    val bondOrderId: Int,
    val maturityOrderId: Int,
    val date: LocalDate,
    val contributionDate: LocalDate,
    val principal: BigDecimal,
    val yieldAmount: BigDecimal,
)
