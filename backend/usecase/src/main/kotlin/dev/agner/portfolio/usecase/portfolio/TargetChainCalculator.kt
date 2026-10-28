package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.portfolio.model.EquitiesChain
import dev.agner.portfolio.usecase.portfolio.model.FixedIncomeChain
import dev.agner.portfolio.usecase.portfolio.model.StrategyChain
import dev.agner.portfolio.usecase.portfolio.model.SubClassChain
import dev.agner.portfolio.usecase.portfolio.model.TargetChain
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerChain
import java.math.BigDecimal

// The one home of the ideal chain's arithmetic: Ideal = Capital × the chain (class % × strategy or
// sub-class share × ticker weight), computed at every rung. A consumer supplies the Capital it wants
// the chain scaled against — the allocation tree uses Capital, the order engine its Investable
// Capital — and each adds its own actuals or units on top.
object TargetChainCalculator {

    private val HUNDRED = BigDecimal("100")

    fun compute(capital: BigDecimal, targets: Targets): TargetChain {
        val classTargets = targets.classes.associate { it.assetClass to it.percentage }
        val classes = AssetClass.entries.map { assetClass ->
            val classIdeal = proportion(amount = capital, percentage = classTargets[assetClass]).defaultScale()
            if (assetClass == AssetClass.RENDA_FIXA) {
                fixedIncome(
                    classTarget = classTargets[assetClass],
                    classIdeal = classIdeal,
                    targets = targets,
                )
            } else {
                equities(
                    assetClass = assetClass,
                    classTarget = classTargets[assetClass],
                    classIdeal = classIdeal,
                    targets = targets,
                )
            }
        }
        return TargetChain(classes)
    }

    private fun equities(
        assetClass: AssetClass,
        classTarget: BigDecimal?,
        classIdeal: BigDecimal,
        targets: Targets,
    ): EquitiesChain {
        val strategies = targets.strategies.filter { it.assetClass == assetClass }.map { strategy ->
            val strategyIdeal = proportion(amount = classIdeal, percentage = strategy.percentage).defaultScale()
            val tickers = targets.tickers.filter { it.strategy == strategy.strategy }.map { ticker ->
                TickerChain(
                    ticker = ticker.ticker,
                    target = ticker.percentage,
                    ideal = proportion(amount = strategyIdeal, percentage = ticker.percentage).defaultScale(),
                )
            }
            StrategyChain(
                strategy = strategy.strategy,
                target = strategy.percentage,
                ideal = strategyIdeal,
                tickers = tickers,
            )
        }

        return EquitiesChain(
            assetClass = assetClass,
            target = classTarget,
            ideal = classIdeal,
            strategies = strategies,
        )
    }

    // Every RF sub-class is always present, so a sub-class the chain does not target still appears
    // with a zero Ideal.
    private fun fixedIncome(
        classTarget: BigDecimal?,
        classIdeal: BigDecimal,
        targets: Targets,
    ): FixedIncomeChain {
        val subClasses = FixedIncomeSubClass.entries.map { subClass ->
            val target = targets.subClasses.firstOrNull { it.subClass == subClass }?.percentage
            SubClassChain(
                subClass = subClass,
                target = target,
                ideal = proportion(amount = classIdeal, percentage = target).defaultScale(),
            )
        }

        return FixedIncomeChain(
            target = classTarget,
            ideal = classIdeal,
            subClasses = subClasses,
        )
    }

    // A percentage is the number itself (50.00 is half), so a share is the amount × pct ÷ 100.
    private fun proportion(amount: BigDecimal, percentage: BigDecimal?): BigDecimal =
        if (percentage == null) BigDecimal.ZERO else amount.multiply(percentage).divide(HUNDRED)
}

// The tickers a Target names with weight > 0, read from the chain leaves — the report-membership half
// of the placement rule. A ticker target under a Strategy the chain does not carry is not a leaf, so
// it is not targeted: the order plan and the placement read the same set.
fun TargetChain.targetedTickers(): Set<String> = classes
    .filterIsInstance<EquitiesChain>()
    .flatMap { it.strategies }
    .flatMap { it.tickers }
    .filter { it.target.signum() > 0 }
    .map { it.ticker }
    .toSet()
