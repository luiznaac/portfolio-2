package dev.agner.portfolio.usecase.bond.consolidation.model

import dev.agner.portfolio.usecase.index.model.IndexValue
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/** The index multiplier is a percentage; four decimals match how brokers quote it. */
private const val MULTIPLIER_SCALE = 4

data class BondContributionConsolidationContext(
    val bondOrderId: Int,
    val contributionDate: LocalDate,
    val dateRange: List<LocalDate>,
    val principal: BigDecimal,
    val yieldAmount: BigDecimal,
    val yieldRates: Map<LocalDate, YieldRateContext>,
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

        data class WithdrawalContext(
            override val id: Int,
            override val amount: BigDecimal,
        ) : RedemptionContext(id, amount) {
            override fun copy(amount: BigDecimal): WithdrawalContext = copy(id = id, amount = amount)
        }
    }

    data class DownToZeroContext(
        val id: Int,
        val date: LocalDate,
    )

    data class YieldRateContext(
        val rate: BigDecimal,
    ) {
        constructor(multiplier: BigDecimal, indexValue: IndexValue) : this(
            rate = multiplier
                .setScale(MULTIPLIER_SCALE, RoundingMode.HALF_EVEN)
                .divide(BigDecimal("100"))
                .multiply(indexValue.value),
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
