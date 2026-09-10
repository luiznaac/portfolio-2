package dev.agner.portfolio.usecase.allocation

import dev.agner.portfolio.usecase.allocation.model.AllocationPlan
import dev.agner.portfolio.usecase.allocation.model.AssetClass
import dev.agner.portfolio.usecase.allocation.model.AssetClassTarget
import dev.agner.portfolio.usecase.allocation.model.ClassNode
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClassTarget
import dev.agner.portfolio.usecase.allocation.model.SubClassNode
import dev.agner.portfolio.usecase.commons.defaultScale
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Pure: ideal = weight × the parent node's ideal, current comes from already-aggregated positions
 * passed in, delta = current − ideal. Ticker-level detail belongs to the order engine, which is
 * where per-strategy targets are resolved.
 */
@Component
class RebalanceCalculator {

    fun calculate(
        capital: BigDecimal,
        classTargets: List<AssetClassTarget>,
        fixedIncomeSubClassTargets: List<FixedIncomeSubClassTarget>,
        currentByClass: Map<AssetClass, BigDecimal>,
        currentBySubClass: Map<FixedIncomeSubClass, BigDecimal>,
    ): AllocationPlan {
        val classWeights = classTargets.associate { it.assetClass to it.weight }
        val subClassWeights = fixedIncomeSubClassTargets.associate { it.subClass to it.weight }

        val classes = AssetClass.entries.map { assetClass ->
            val weight = classWeights[assetClass] ?: BigDecimal.ZERO
            val ideal = (weight * capital).defaultScale()
            val current = (currentByClass[assetClass] ?: BigDecimal.ZERO).defaultScale()

            val subClasses = if (assetClass == AssetClass.FIXED_INCOME) {
                FixedIncomeSubClass.entries.map { subClass ->
                    val subWeight = subClassWeights[subClass] ?: BigDecimal.ZERO
                    SubClassNode(
                        subClass = subClass,
                        idealWeight = subWeight,
                        ideal = (subWeight * ideal).defaultScale(),
                        current = (currentBySubClass[subClass] ?: BigDecimal.ZERO).defaultScale(),
                    )
                }
            } else {
                emptyList()
            }

            ClassNode(
                assetClass = assetClass,
                idealWeight = weight,
                ideal = ideal,
                current = current,
                subClasses = subClasses,
            )
        }

        return AllocationPlan(capital = capital.defaultScale(), classes = classes)
    }
}
