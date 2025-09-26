package dev.agner.portfolio.usecase.bond.consolidation.model

sealed class BondCalculationResult(
    open val principal: Double,
    open val yield: Double,
    open val statements: List<BondCalculationRecord>,
) {
    data class Ok(
        override val principal: Double,
        override val yield: Double,
        override val statements: List<BondCalculationRecord>
    ) : BondCalculationResult(principal, yield, statements)

    data class RemainingRedemption(
        override val principal: Double,
        override val yield: Double,
        override val statements: List<BondCalculationRecord>,
        val remainingRedemptionAmount: Double,
    ) : BondCalculationResult(principal, yield, statements)
}

sealed class BondCalculationRecord(
    open val amount: Double,
) {
    data class Yield(override val amount: Double) : BondCalculationRecord(amount)
    data class PrincipalRedeem(override val amount: Double) : BondCalculationRecord(amount)
    data class YieldRedeem(override val amount: Double) : BondCalculationRecord(amount)
}
