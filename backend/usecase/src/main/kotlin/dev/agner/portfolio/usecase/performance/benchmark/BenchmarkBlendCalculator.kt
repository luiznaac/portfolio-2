package dev.agner.portfolio.usecase.performance.benchmark

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.performance.HUNDRED
import dev.agner.portfolio.usecase.performance.PERFORMANCE_SCALE
import dev.agner.portfolio.usecase.portfolio.model.SubClassTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import java.math.BigDecimal
import java.math.RoundingMode

// The target-weighted blend: total benchmark = Σ (class target % × class benchmark return). Ações and
// FIIs are equity exposure and read IBOV; Renda Fixa blends its sub-classes (Pós/Pré → CDI, Inflação
// → IPCA) by their own targets, so the RF class contributes its sub-class-weighted return.
object BenchmarkBlendCalculator {

    fun compute(targets: Targets, returns: Map<BenchmarkIndex, BigDecimal?>): BenchmarkBlend {
        val contributions = contributions(targets, returns)
        val targeted = contributions.filter { it.target.signum() > 0 }
        val totalTarget = targeted.fold(BigDecimal.ZERO) { acc, contribution -> acc + contribution.target }

        // The denominator is the policy's own target weight — never the weight the data happens to
        // price. A targeted leg with no benchmark makes the blended total unknown rather than
        // silently renormalising the others to 100%: a partial blend is not the policy benchmark.
        val complete = targeted.all { it.returnPercentage != null }
        val blended = if (totalTarget.signum() == 0 || !complete) {
            null
        } else {
            targeted.fold(BigDecimal.ZERO) { acc, contribution ->
                acc + contribution.target * (contribution.returnPercentage ?: BigDecimal.ZERO)
            }.divide(totalTarget, PERFORMANCE_SCALE, RoundingMode.HALF_UP)
        }

        return BenchmarkBlend(blended, contributions)
    }

    private fun contributions(targets: Targets, returns: Map<BenchmarkIndex, BigDecimal?>) =
        targets.classes.flatMap { classTarget ->
            when (classTarget.assetClass) {
                AssetClass.ACOES, AssetClass.FIIS -> listOf(
                    BenchmarkContribution(
                        label = classTarget.assetClass.name,
                        target = classTarget.percentage,
                        benchmark = BenchmarkIndex.IBOV,
                        returnPercentage = returns[BenchmarkIndex.IBOV],
                    ),
                )

                AssetClass.RENDA_FIXA -> targets.subClasses.map { subClass ->
                    val weight = classTarget.percentage
                        .multiply(subClass.percentage)
                        .divide(HUNDRED)
                    val index = subClass.benchmarkIndex()
                    BenchmarkContribution(
                        label = "RENDA_FIXA.${subClass.subClass.name}",
                        target = weight,
                        benchmark = index,
                        returnPercentage = returns[index],
                    )
                }
            }
        }
}

private fun SubClassTarget.benchmarkIndex() =
    when (subClass) {
        FixedIncomeSubClass.FIXED_RATE, FixedIncomeSubClass.FLOATING_RATE -> BenchmarkIndex.CDI
        FixedIncomeSubClass.INFLATION_LINKED -> BenchmarkIndex.IPCA
    }
