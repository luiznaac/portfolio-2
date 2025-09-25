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
}

sealed class BondCalculationRecord(
    open val amount: Double,
) {
    data class Yield(override val amount: Double) : BondCalculationRecord(amount)
}
