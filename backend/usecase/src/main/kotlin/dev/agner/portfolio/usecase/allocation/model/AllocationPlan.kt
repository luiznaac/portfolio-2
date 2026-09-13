package dev.agner.portfolio.usecase.allocation.model

import java.math.BigDecimal

// The output of RebalanceCalculator: capital -> class -> (fixed income only, for now) sub-class.
// Ticker-level detail belongs to the order engine, which is where per-strategy targets resolve.
data class AllocationPlan(
    val capital: BigDecimal,
    val classes: List<ClassNode>,
)

data class ClassNode(
    val assetClass: AssetClass,
    val idealWeight: BigDecimal,
    val ideal: BigDecimal,
    val current: BigDecimal,
    val subClasses: List<SubClassNode> = emptyList(),
) {
    val delta: BigDecimal get() = current - ideal
}

data class SubClassNode(
    val subClass: FixedIncomeSubClass,
    val idealWeight: BigDecimal,
    val ideal: BigDecimal,
    val current: BigDecimal,
) {
    val delta: BigDecimal get() = current - ideal
}
