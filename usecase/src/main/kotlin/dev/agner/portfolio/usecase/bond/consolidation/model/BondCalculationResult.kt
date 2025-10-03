package dev.agner.portfolio.usecase.bond.consolidation.model

import java.math.BigDecimal

sealed class BondCalculationResult(
    open val principal: BigDecimal,
    open val yield: BigDecimal,
    open val statements: List<BondCalculationRecord>,
) {
    data class Ok(
        override val principal: BigDecimal,
        override val yield: BigDecimal,
        override val statements: List<BondCalculationRecord>
    ) : BondCalculationResult(principal, yield, statements)

    data class RemainingRedemption(
        override val principal: BigDecimal,
        override val yield: BigDecimal,
        override val statements: List<BondCalculationRecord>,
        val remainingRedemptionAmount: BigDecimal,
    ) : BondCalculationResult(principal, yield, statements)
}

sealed class BondCalculationRecord(
    open val amount: BigDecimal,
) {
    data class Yield(override val amount: BigDecimal) : BondCalculationRecord(amount)
    data class PrincipalRedeem(override val amount: BigDecimal) : BondCalculationRecord(amount)
    data class YieldRedeem(override val amount: BigDecimal) : BondCalculationRecord(amount)
    data class TaxRedeem(override val amount: BigDecimal, val taxType: String) : BondCalculationRecord(amount)
}
