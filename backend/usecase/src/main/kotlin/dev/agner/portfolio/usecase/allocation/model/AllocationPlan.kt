package dev.agner.portfolio.usecase.allocation.model

import java.math.BigDecimal

// The output of RebalanceCalculator: Capital -> Classe -> (Renda Fixa only, for now) Sub-classe.
// Ticker-level detail for Ações/FIIs needs per-strategy targets, which don't exist until Fase 2 —
// so this tree stops one level short of the plan's full Capital -> Classe -> Estratégia -> Ticker
// shape until then.
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
