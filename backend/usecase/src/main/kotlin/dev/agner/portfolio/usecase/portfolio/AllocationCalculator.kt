package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.portfolio.model.Allocation
import dev.agner.portfolio.usecase.portfolio.model.ClassAllocation
import dev.agner.portfolio.usecase.portfolio.model.StrategyAllocation
import dev.agner.portfolio.usecase.portfolio.model.SubClassAllocation
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerAllocation
import kotlinx.datetime.YearMonth
import java.math.BigDecimal
import java.math.RoundingMode

// One valued listed holding crossing into the calculator: the ticker, the class the catalog gives it
// (null when the catalog has not classified it) and the units × the current quote.
data class ListedHolding(
    val ticker: String,
    val assetClass: AssetClass?,
    val quantity: BigDecimal,
    val price: BigDecimal,
) {
    val value: BigDecimal get() = (quantity * price).defaultScale()
}

// One valued RF product — its derived sub-class and its current value (principal + yield).
data class FixedIncomeHolding(
    val subClass: FixedIncomeSubClass,
    val value: BigDecimal,
)

// The rebalancing chain's arithmetic, pure and repository-free so it is unit-tested on hand-worked
// numbers (backend/AGENTS.md: a calculation-heavy feature gets its own class).
//
// Ideal = Capital × the chain (class % × strategy/sub-class share × ticker weight). Actual comes from
// two sources the engine already holds: the RF consolidation values and Position × current quote.
// Deficit = Ideal − Actual (positive = short); Drift = (Actual − Ideal) ÷ Capital, in percentage
// points.
//
// The listed book is valued at the class level only: the strategy and ticker rungs carry their plan
// but no attributed value yet, and the whole listed book is the unattributed segment. Attribution is
// its own slice — the stored Attribution ledger that maps a held quantity across strategies — so
// nothing maps a ticker to a strategy today, the same reason the Ativos detail shows the whole
// position as "não atribuído".
object AllocationCalculator {

    private val HUNDRED = BigDecimal("100")

    private const val DRIFT_SCALE = 2

    fun compute(
        period: YearMonth,
        capital: BigDecimal,
        floor: BigDecimal,
        targets: Targets,
        listed: List<ListedHolding>,
        fixedIncome: List<FixedIncomeHolding>,
    ): Allocation {
        val classTargets = targets.classes.associate { it.assetClass to it.percentage }

        val listedByClass = listed.sumBy({ it.assetClass }, { it.value })
        val unclassified = listed.filter { it.assetClass == null }.sumOf { it.value }.defaultScale()
        val fixedIncomeBySubClass = fixedIncome.sumBy({ it.subClass }, { it.value })

        val classes = AssetClass.entries.map { assetClass ->
            val classIdeal = proportion(capital, classTargets[assetClass]).defaultScale()
            if (assetClass == AssetClass.RENDA_FIXA) {
                fixedIncome(
                    classIdeal = classIdeal,
                    classTarget = classTargets[assetClass],
                    targets = targets,
                    fixedIncomeBySubClass = fixedIncomeBySubClass,
                    capital = capital,
                )
            } else {
                equities(
                    assetClass = assetClass,
                    classIdeal = classIdeal,
                    classTarget = classTargets[assetClass],
                    targets = targets,
                    listedValue = listedByClass[assetClass] ?: BigDecimal.ZERO,
                    capital = capital,
                )
            }
        }

        val actual = (classes.sumOf { it.actual } + unclassified).defaultScale()
        val ideal = capital.defaultScale()

        return Allocation(
            period = period,
            capital = ideal,
            floor = floor.defaultScale(),
            ideal = ideal,
            actual = actual,
            deficit = (ideal - actual).defaultScale(),
            drift = drift(actual, ideal, capital),
            unattributed = (classes.sumOf { it.unattributed } + unclassified).defaultScale(),
            classes = classes,
        )
    }

    // ACOES/FIIS: the class's listed value is unattributed, so its strategies and their tickers carry
    // the plan (Ideal) with no Actual yet.
    private fun equities(
        assetClass: AssetClass,
        classIdeal: BigDecimal,
        classTarget: BigDecimal?,
        targets: Targets,
        listedValue: BigDecimal,
        capital: BigDecimal,
    ): ClassAllocation {
        val strategies = targets.strategies.filter { it.assetClass == assetClass }.map { strategy ->
            val strategyIdeal = proportion(classIdeal, strategy.percentage).defaultScale()
            val tickers = targets.tickers.filter { it.strategy == strategy.strategy }.map { ticker ->
                val tickerIdeal = proportion(strategyIdeal, ticker.percentage).defaultScale()
                TickerAllocation(
                    ticker = ticker.ticker,
                    target = ticker.percentage,
                    ideal = tickerIdeal,
                    actual = BigDecimal.ZERO,
                    deficit = tickerIdeal,
                    drift = drift(BigDecimal.ZERO, tickerIdeal, capital),
                )
            }
            StrategyAllocation(
                strategy = strategy.strategy,
                target = strategy.percentage,
                ideal = strategyIdeal,
                actual = BigDecimal.ZERO,
                deficit = strategyIdeal,
                drift = drift(BigDecimal.ZERO, strategyIdeal, capital),
                tickers = tickers,
            )
        }

        return ClassAllocation(
            assetClass = assetClass,
            target = classTarget,
            ideal = classIdeal,
            actual = listedValue.defaultScale(),
            deficit = (classIdeal - listedValue).defaultScale(),
            drift = drift(listedValue, classIdeal, capital),
            unattributed = listedValue.defaultScale(),
            strategies = strategies,
            subClasses = emptyList(),
        )
    }

    // RENDA_FIXA: every RF sub-class is always present, so a product whose sub-class the chain does
    // not target still counts toward the class Actual.
    private fun fixedIncome(
        classIdeal: BigDecimal,
        classTarget: BigDecimal?,
        targets: Targets,
        fixedIncomeBySubClass: Map<FixedIncomeSubClass, BigDecimal>,
        capital: BigDecimal,
    ): ClassAllocation {
        val subClasses = FixedIncomeSubClass.entries.map { subClass ->
            val target = targets.subClasses.firstOrNull { it.subClass == subClass }?.percentage
            val subClassIdeal = proportion(classIdeal, target).defaultScale()
            val subClassActual = (fixedIncomeBySubClass[subClass] ?: BigDecimal.ZERO).defaultScale()
            SubClassAllocation(
                subClass = subClass,
                target = target,
                ideal = subClassIdeal,
                actual = subClassActual,
                deficit = (subClassIdeal - subClassActual).defaultScale(),
                drift = drift(subClassActual, subClassIdeal, capital),
            )
        }

        val actual = subClasses.sumOf { it.actual }.defaultScale()

        return ClassAllocation(
            assetClass = AssetClass.RENDA_FIXA,
            target = classTarget,
            ideal = classIdeal,
            actual = actual,
            deficit = (classIdeal - actual).defaultScale(),
            drift = drift(actual, classIdeal, capital),
            unattributed = BigDecimal.ZERO,
            strategies = emptyList(),
            subClasses = subClasses,
        )
    }

    // A percentage is the number itself (50.00 is half), so a share is the amount × pct ÷ 100.
    private fun proportion(amount: BigDecimal, percentage: BigDecimal?): BigDecimal =
        if (percentage == null) BigDecimal.ZERO else amount.multiply(percentage).divide(HUNDRED)

    private fun drift(actual: BigDecimal, ideal: BigDecimal, capital: BigDecimal): BigDecimal =
        if (capital.signum() == 0) {
            BigDecimal.ZERO
        } else {
            (actual - ideal).multiply(HUNDRED).divide(capital, DRIFT_SCALE, RoundingMode.HALF_UP)
        }
}

// Sums a value per non-null key in one pass — the shape every rung aggregation shares.
private fun <K, T> Iterable<T>.sumBy(key: (T) -> K?, value: (T) -> BigDecimal): Map<K, BigDecimal> =
    mapNotNull { item -> key(item)?.let { it to value(item) } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, amounts) -> amounts.sumOf { amount -> amount } }
