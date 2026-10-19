package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.SubClassTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerTarget
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The allocation tree is a pure composition of (Capital, target chain, RF product values, listed
// holdings). These tests pin the arithmetic — the chain's ideals, the RF + listed actuals, the
// deficit/drift signs and the unattributed segment — with hand-worked numbers, no repositories.
class AllocationCalculatorTest : StringSpec({

    "composes RF and listed actuals into the class actuals and the Capital root" {
        val allocation = compute(
            capital = "100000",
            targets = targets(
                classTarget(AssetClass.ACOES, "50"),
                classTarget(AssetClass.RENDA_FIXA, "50"),
            ),
            listed = listOf(listed("VALE3", AssetClass.ACOES, quantity = "100", price = "10")),
            fixedIncome = listOf(fixedIncome(FixedIncomeSubClass.FLOATING_RATE, "2000")),
        )

        val acoes = allocation.classes.single { it.assetClass == AssetClass.ACOES }
        val fixedIncome = allocation.classes.single { it.assetClass == AssetClass.RENDA_FIXA }

        acoes.ideal.plain() shouldBe "50000"
        acoes.actual.plain() shouldBe "1000"
        fixedIncome.ideal.plain() shouldBe "50000"
        fixedIncome.actual.plain() shouldBe "2000"

        allocation.ideal.plain() shouldBe "100000"
        allocation.actual.plain() shouldBe "3000"
        allocation.deficit.plain() shouldBe "97000"
        allocation.drift.plain() shouldBe "-97"
        allocation.unattributed.plain() shouldBe "1000"
    }

    "computes Ideal at every chain rung as Capital × the chain" {
        val allocation = compute(
            capital = "100000",
            targets = Targets(
                classes = listOf(classTarget(AssetClass.ACOES, "50")),
                strategies = listOf(strategyTarget(AssetClass.ACOES, "Top Dividendos", "40")),
                subClasses = emptyList(),
                tickers = listOf(tickerTarget("Top Dividendos", "VALE3", "25")),
            ),
            listed = listOf(listed("VALE3", AssetClass.ACOES, quantity = "100", price = "10")),
            fixedIncome = emptyList(),
        )

        val strategy = allocation.classes
            .single { it.assetClass == AssetClass.ACOES }
            .strategies
            .single()

        strategy.ideal.plain() shouldBe "20000"
        strategy.tickers.single().ideal.plain() shouldBe "5000"
    }

    "the whole listed book is unattributed until the Attribution ledger lands" {
        val allocation = compute(
            capital = "100000",
            targets = chainWithStrategy(tickerTarget("Top Dividendos", "VALE3", "100")),
            listed = listOf(listed("VALE3", AssetClass.ACOES, "100", "10")),
            fixedIncome = emptyList(),
        )

        val acoes = allocation.classes.single { it.assetClass == AssetClass.ACOES }
        val strategy = acoes.strategies.single()

        strategy.actual.plain() shouldBe "0"
        strategy.tickers.single().actual.plain() shouldBe "0"
        acoes.actual.plain() shouldBe "1000"
        acoes.unattributed.plain() shouldBe "1000"
        allocation.unattributed.plain() shouldBe "1000"
    }

    "a held ticker the chain does not name is still part of the unattributed segment" {
        val allocation = compute(
            capital = "100000",
            targets = chainWithStrategy(tickerTarget("Top Dividendos", "VALE3", "25")),
            listed = listOf(
                listed("VALE3", AssetClass.ACOES, "100", "10"),
                listed("ITUB4", AssetClass.ACOES, "50", "10"),
            ),
            fixedIncome = emptyList(),
        )

        val acoes = allocation.classes.single { it.assetClass == AssetClass.ACOES }

        acoes.actual.plain() shouldBe "1500"
        acoes.unattributed.plain() shouldBe "1500"
        allocation.unattributed.plain() shouldBe "1500"
    }

    "computes the RF sub-class ideals and actuals" {
        val allocation = compute(
            capital = "100000",
            targets = Targets(
                classes = listOf(classTarget(AssetClass.RENDA_FIXA, "50")),
                strategies = emptyList(),
                subClasses = listOf(
                    subClassTarget(FixedIncomeSubClass.FLOATING_RATE, "60"),
                    subClassTarget(FixedIncomeSubClass.INFLATION_LINKED, "40"),
                ),
                tickers = emptyList(),
            ),
            listed = emptyList(),
            fixedIncome = listOf(
                fixedIncome(FixedIncomeSubClass.FLOATING_RATE, "2000"),
                fixedIncome(FixedIncomeSubClass.INFLATION_LINKED, "1000"),
            ),
        )

        val subClasses = allocation.classes.single { it.assetClass == AssetClass.RENDA_FIXA }.subClasses

        val floating = subClasses.single { it.subClass == FixedIncomeSubClass.FLOATING_RATE }
        floating.ideal.plain() shouldBe "30000"
        floating.actual.plain() shouldBe "2000"
        floating.deficit.plain() shouldBe "28000"

        subClasses.single { it.subClass == FixedIncomeSubClass.INFLATION_LINKED }
            .ideal.plain() shouldBe "20000"
    }

    "a class with no target row has a zero Ideal and so sits in excess" {
        val allocation = compute(
            capital = "100000",
            targets = emptyTargets(),
            listed = listOf(listed("MXRF11", AssetClass.FIIS, "100", "10")),
            fixedIncome = emptyList(),
        )

        val fiis = allocation.classes.single { it.assetClass == AssetClass.FIIS }

        fiis.target shouldBe null
        fiis.ideal.plain() shouldBe "0"
        fiis.actual.plain() shouldBe "1000"
        fiis.deficit.plain() shouldBe "-1000"
    }

    "an unclassified holding joins neither a class nor a strategy, only the total and the unattributed" {
        val allocation = compute(
            capital = "100000",
            targets = chainWithStrategy(tickerTarget("Top Dividendos", "VALE3", "100")),
            listed = listOf(
                listed("VALE3", AssetClass.ACOES, "100", "10"),
                listed("ZZZZ9", null, "10", "10"),
            ),
            fixedIncome = emptyList(),
        )

        allocation.classes.single { it.assetClass == AssetClass.ACOES }.actual.plain() shouldBe "1000"
        allocation.actual.plain() shouldBe "1100"
        allocation.unattributed.plain() shouldBe "1100"
    }

    "a zero capital never divides by zero" {
        val allocation = compute(
            capital = "0",
            targets = emptyTargets(),
            listed = emptyList(),
            fixedIncome = emptyList(),
        )

        allocation.ideal.plain() shouldBe "0"
        allocation.actual.plain() shouldBe "0"
        allocation.drift.plain() shouldBe "0"
    }
})

private fun chainWithStrategy(vararg tickers: TickerTarget) = Targets(
    classes = listOf(classTarget(AssetClass.ACOES, "50")),
    strategies = listOf(strategyTarget(AssetClass.ACOES, "Top Dividendos", "100")),
    subClasses = emptyList(),
    tickers = tickers.toList(),
)

private fun emptyTargets() = Targets(emptyList(), emptyList(), emptyList(), emptyList())

private fun compute(
    capital: String,
    targets: Targets,
    listed: List<ListedHolding>,
    fixedIncome: List<FixedIncomeHolding>,
) = AllocationCalculator.compute(
    period = YearMonth.parse("2026-09"),
    capital = BigDecimal(capital),
    floor = BigDecimal("20000"),
    targets = targets,
    listed = listed,
    fixedIncome = fixedIncome,
)

private fun targets(vararg classes: ClassTarget) =
    Targets(classes.toList(), emptyList(), emptyList(), emptyList())

private fun classTarget(assetClass: AssetClass, percentage: String) =
    ClassTarget(assetClass, BigDecimal(percentage))

private fun strategyTarget(assetClass: AssetClass, strategy: String, percentage: String) =
    StrategyTarget(assetClass, strategy, BigDecimal(percentage))

private fun subClassTarget(subClass: FixedIncomeSubClass, percentage: String) =
    SubClassTarget(subClass, BigDecimal(percentage))

private fun tickerTarget(strategy: String, ticker: String, percentage: String) =
    TickerTarget(strategy, ticker, BigDecimal(percentage))

private fun listed(ticker: String, assetClass: AssetClass?, quantity: String, price: String) =
    ListedHolding(ticker, assetClass, BigDecimal(quantity), BigDecimal(price))

private fun fixedIncome(subClass: FixedIncomeSubClass, value: String) =
    FixedIncomeHolding(subClass, BigDecimal(value))

private fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()
