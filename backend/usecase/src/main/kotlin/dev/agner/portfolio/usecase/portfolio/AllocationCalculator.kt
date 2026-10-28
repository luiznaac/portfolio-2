package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.deficitAgainst
import dev.agner.portfolio.usecase.commons.valueAt
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.portfolio.model.ActualTickerAllocation
import dev.agner.portfolio.usecase.portfolio.model.Allocation
import dev.agner.portfolio.usecase.portfolio.model.ClassAllocation
import dev.agner.portfolio.usecase.portfolio.model.EquitiesChain
import dev.agner.portfolio.usecase.portfolio.model.FixedIncomeChain
import dev.agner.portfolio.usecase.portfolio.model.ManualAllocation
import dev.agner.portfolio.usecase.portfolio.model.OutsideAllocation
import dev.agner.portfolio.usecase.portfolio.model.StrategyAllocation
import dev.agner.portfolio.usecase.portfolio.model.SubClassAllocation
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerAllocation
import dev.agner.portfolio.usecase.portfolio.model.UnclassifiedAllocation
import dev.agner.portfolio.usecase.strategy.model.AttributionLedger
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
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

// A Manual Strategy (the legacy bucket) crossing into the calculator: one per class, identified by its
// own name so its attribution slices can be joined.
data class ManualStrategyHolding(
    val name: String,
    val assetClass: AssetClass,
)

// One valued RF product — its derived sub-class and its current value (principal + yield).
data class FixedIncomeHolding(
    val subClass: FixedIncomeSubClass,
    val value: BigDecimal,
)

// The allocation tree's arithmetic. The ideal chain itself lives in TargetChainCalculator (shared
// with the order plan); this class adds the Actuals — the RF consolidation values and the listed
// book split across report Strategies, the manual bucket and the outside remainder — and derives
// Deficit (Ideal − Actual, positive = short) and Drift ((Actual − Ideal) ÷ Capital, in percentage
// points) at every planned rung.
//
// A strategy row's Actual is its attributed units × the current quote, never the whole custody of a
// ticker it names. The class Actual stays full custody, so the class's rows sum to it by construction
// and the class Deficit is the gap the plan's Attribution Transfers close.
object AllocationCalculator {

    private val HUNDRED = BigDecimal("100")

    private const val DRIFT_SCALE = 2

    fun compute(
        period: YearMonth?,
        capital: BigDecimal,
        floor: BigDecimal,
        targets: Targets,
        listed: List<ListedHolding>,
        fixedIncome: List<FixedIncomeHolding>,
        attributions: AttributionLedger = emptyMap(),
        manualStrategies: List<ManualStrategyHolding> = emptyList(),
    ): Allocation {
        val chain = TargetChainCalculator.compute(capital, targets)
        val listedByClass = listed
            .mapNotNull { holding -> holding.assetClass?.let { assetClass -> assetClass to holding } }
            .groupBy({ it.first }, { it.second })
        val unclassified = unclassifiedAllocation(listed.filter { it.assetClass == null })
        val fixedIncomeBySubClass = fixedIncome.sumBy({ it.subClass }, { it.value })

        val classes = chain.classes.map { classChain ->
            when (classChain) {
                is EquitiesChain -> equities(
                    chain = classChain,
                    holdings = listedByClass[classChain.assetClass].orEmpty(),
                    attributions = attributions,
                    manualStrategies = manualStrategies,
                    capital = capital,
                )

                is FixedIncomeChain -> fixedIncome(
                    chain = classChain,
                    fixedIncomeBySubClass = fixedIncomeBySubClass,
                    capital = capital,
                )
            }
        }

        val actual = (classes.sumOf { it.actual } + unclassified.actual).defaultScale()
        val ideal = capital.defaultScale()

        return Allocation(
            period = period,
            capital = ideal,
            floor = floor.defaultScale(),
            ideal = ideal,
            actual = actual,
            deficit = ideal.deficitAgainst(actual),
            drift = drift(actual = actual, ideal = ideal, capital = capital),
            unattributed = (classes.sumOf { it.unattributed } + unclassified.actual).defaultScale(),
            unclassified = unclassified,
            classes = classes,
        )
    }

    // ACOES/FIIS: the class's custody is split into its report Strategies' attributed slices, the
    // manual bucket's attributed slices and the outside remainder, so the three sum to the class
    // Actual.
    private fun equities(
        chain: EquitiesChain,
        holdings: List<ListedHolding>,
        attributions: AttributionLedger,
        manualStrategies: List<ManualStrategyHolding>,
        capital: BigDecimal,
    ): ClassAllocation {
        val priceByTicker = holdings.associate { it.ticker to it.price }
        val custodyByTicker = holdings.groupBy { it.ticker }.mapValues { (_, rows) ->
            rows.sumOf { it.quantity }
        }
        val classActual = holdings.sumOf { it.value }.defaultScale()

        val strategies = chain.strategies.map { strategy ->
            val tickers = strategy.tickers.map { ticker ->
                val attributed = attributions[StrategyTicker(strategy.strategy, ticker.ticker)] ?: BigDecimal.ZERO
                val actual = attributed.valueAt(price = priceByTicker[ticker.ticker])
                TickerAllocation(
                    ticker = ticker.ticker,
                    target = ticker.target,
                    ideal = ticker.ideal,
                    actual = actual,
                    deficit = ticker.ideal.deficitAgainst(actual),
                    drift = drift(actual = actual, ideal = ticker.ideal, capital = capital),
                )
            }
            val actual = tickers.sumOf { it.actual }.defaultScale()
            StrategyAllocation(
                strategy = strategy.strategy,
                target = strategy.target,
                ideal = strategy.ideal,
                actual = actual,
                deficit = strategy.ideal.deficitAgainst(actual),
                drift = drift(actual = actual, ideal = strategy.ideal, capital = capital),
                tickers = tickers,
            )
        }

        val manualStrategy = manualStrategies.firstOrNull { it.assetClass == chain.assetClass }
        val manualSlices = manualStrategy?.let { manual ->
            attributions.filterKeys { key -> key.strategy == manual.name && priceByTicker.containsKey(key.ticker) }
        }.orEmpty()
        val manualTickers = manualSlices
            .map { (key, quantity) ->
                ActualTickerAllocation(key.ticker, quantity.valueAt(price = priceByTicker[key.ticker]))
            }
            .sortedBy { it.ticker }
        val manual = manualStrategy?.let {
            ManualAllocation(
                strategy = it.name,
                actual = manualTickers.sumOf { ticker -> ticker.actual }.defaultScale(),
                tickers = manualTickers,
            )
        }

        // Only a report Strategy's target tickers and the bucket's slices count as attributed: an
        // attribution to a report Strategy with no Target is inert and stays outside.
        val reportAttributed = chain.strategies.flatMap { strategy ->
            strategy.tickers.mapNotNull { ticker ->
                val key = StrategyTicker(strategy.strategy, ticker.ticker)
                attributions[key]?.let { quantity -> ticker.ticker to quantity }
            }
        }
        val manualAttributed = manualSlices.map { (key, quantity) -> key.ticker to quantity }
        val countedByTicker = (reportAttributed + manualAttributed)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, quantities) -> quantities.sumOf { it } }

        val outsideTickers = custodyByTicker.mapNotNull { (ticker, custody) ->
            val remainder = custody - (countedByTicker[ticker] ?: BigDecimal.ZERO)
            if (remainder.signum() <= 0) {
                null
            } else {
                ActualTickerAllocation(ticker, remainder.valueAt(price = priceByTicker[ticker]))
            }
        }.sortedBy { it.ticker }
        val outsideActual = (
            classActual -
                strategies.sumOf { it.actual } -
                (manual?.actual ?: BigDecimal.ZERO)
            ).defaultScale()

        return ClassAllocation(
            assetClass = chain.assetClass,
            target = chain.target,
            ideal = chain.ideal,
            actual = classActual,
            deficit = chain.ideal.deficitAgainst(classActual),
            drift = drift(actual = classActual, ideal = chain.ideal, capital = capital),
            unattributed = outsideActual,
            strategies = strategies,
            manual = manual,
            outside = OutsideAllocation(actual = outsideActual, tickers = outsideTickers),
            subClasses = emptyList(),
        )
    }

    // RENDA_FIXA: every RF sub-class is always present, so a product whose sub-class the chain does
    // not target still counts toward the class Actual.
    private fun fixedIncome(
        chain: FixedIncomeChain,
        fixedIncomeBySubClass: Map<FixedIncomeSubClass, BigDecimal>,
        capital: BigDecimal,
    ): ClassAllocation {
        val subClasses = chain.subClasses.map { subClass ->
            val actual = (fixedIncomeBySubClass[subClass.subClass] ?: BigDecimal.ZERO).defaultScale()
            SubClassAllocation(
                subClass = subClass.subClass,
                target = subClass.target,
                ideal = subClass.ideal,
                actual = actual,
                deficit = subClass.ideal.deficitAgainst(actual),
                drift = drift(actual = actual, ideal = subClass.ideal, capital = capital),
            )
        }

        val actual = subClasses.sumOf { it.actual }.defaultScale()

        return ClassAllocation(
            assetClass = AssetClass.RENDA_FIXA,
            target = chain.target,
            ideal = chain.ideal,
            actual = actual,
            deficit = chain.ideal.deficitAgainst(actual),
            drift = drift(actual = actual, ideal = chain.ideal, capital = capital),
            unattributed = BigDecimal.ZERO,
            strategies = emptyList(),
            manual = null,
            outside = OutsideAllocation(actual = BigDecimal.ZERO, tickers = emptyList()),
            subClasses = subClasses,
        )
    }

    private fun unclassifiedAllocation(holdings: List<ListedHolding>): UnclassifiedAllocation {
        val tickers = holdings
            .groupBy { it.ticker }
            .map { (ticker, rows) ->
                ActualTickerAllocation(ticker, rows.sumOf { it.value }.defaultScale())
            }
            .sortedBy { it.ticker }
        return UnclassifiedAllocation(
            actual = tickers.sumOf { it.actual }.defaultScale(),
            tickers = tickers,
        )
    }

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
