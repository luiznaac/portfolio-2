package dev.agner.portfolio.usecase.performance.benchmark

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.performance.PERFORMANCE_SCALE
import java.math.BigDecimal
import java.math.RoundingMode

// One line of the policy benchmark: the class (or RF sub-class) and the effective target weight it
// contributes. `benchmark` is null only for the RF class rung, which is itself a sub-class blend.
data class BenchmarkContribution(
    val label: String,
    val target: BigDecimal,
    val benchmark: BenchmarkIndex?,
    val returnPercentage: BigDecimal?,
)

// The total benchmark the portfolio is compared against: each class benchmark weighted by the
// month's target chain (the policy allocation, never the actual one). A return the SGS series could
// not price drops out and the remaining weights are renormalised, so a data gap never reads as zero.
data class BenchmarkBlend(
    val returnPercentage: BigDecimal?,
    val contributions: List<BenchmarkContribution>,
)

// The benchmark line a class rung is compared against. Ações and FIIs read their IBOV contribution;
// Renda Fixa is the sub-class-weighted CDI/IPCA blend its own contributions already carry. Null when
// the class's benchmark could not be priced.
fun BenchmarkBlend.classBenchmark(assetClass: AssetClass): BigDecimal? = when (assetClass) {
    AssetClass.ACOES, AssetClass.FIIS ->
        contributions.firstOrNull { it.label == assetClass.name }?.returnPercentage

    AssetClass.RENDA_FIXA -> {
        val rf = contributions.filter { it.label.startsWith("RENDA_FIXA.") && it.returnPercentage != null }
        val total = rf.fold(BigDecimal.ZERO) { acc, contribution -> acc + contribution.target }
        if (total.signum() == 0) {
            null
        } else {
            rf.fold(BigDecimal.ZERO) { acc, contribution ->
                acc + contribution.target * (contribution.returnPercentage ?: BigDecimal.ZERO)
            }.divide(total, PERFORMANCE_SCALE, RoundingMode.HALF_UP)
        }
    }
}
