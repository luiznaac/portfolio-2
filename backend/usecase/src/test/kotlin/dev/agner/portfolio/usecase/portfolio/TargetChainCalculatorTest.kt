package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.classTarget
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.portfolio.model.EquitiesChain
import dev.agner.portfolio.usecase.portfolio.model.FixedIncomeChain
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.strategyTarget
import dev.agner.portfolio.usecase.subClassTarget
import dev.agner.portfolio.usecase.tickerTarget
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

// The ideal chain is one arithmetic shared by the allocation tree and the order plan. These tests pin
// its rungs directly, so both consumers can compose it without re-deriving Capital × chain.
class TargetChainCalculatorTest : StringSpec({

    "computes the Ideal at every rung from the Capital" {
        val chain = TargetChainCalculator.compute(
            capital = BigDecimal("100000"),
            targets = Targets(
                classes = listOf(
                    classTarget(AssetClass.ACOES, "50"),
                    classTarget(AssetClass.RENDA_FIXA, "50"),
                ),
                strategies = listOf(strategyTarget(AssetClass.ACOES, "Top", "40")),
                subClasses = listOf(
                    subClassTarget(FixedIncomeSubClass.FLOATING_RATE, "60"),
                    subClassTarget(FixedIncomeSubClass.INFLATION_LINKED, "40"),
                ),
                tickers = listOf(tickerTarget("Top", "VALE3", "25")),
            ),
        )

        val acoes = chain.classes.filterIsInstance<EquitiesChain>().single { it.assetClass == AssetClass.ACOES }
        acoes.ideal.plain() shouldBe "50000"
        acoes.strategies.single().ideal.plain() shouldBe "20000"
        acoes.strategies.single().tickers.single().ideal.plain() shouldBe "5000"

        val fixedIncome = chain.classes.filterIsInstance<FixedIncomeChain>().single()
        fixedIncome.ideal.plain() shouldBe "50000"
        fixedIncome.subClasses.single { it.subClass == FixedIncomeSubClass.FLOATING_RATE }
            .ideal.plain() shouldBe "30000"
        fixedIncome.subClasses.single { it.subClass == FixedIncomeSubClass.INFLATION_LINKED }
            .ideal.plain() shouldBe "20000"
        fixedIncome.subClasses.single { it.subClass == FixedIncomeSubClass.FIXED_RATE }.let {
            it.ideal.plain() shouldBe "0"
            it.target shouldBe null
        }
    }

    "always carries the three asset classes and the three RF sub-classes" {
        val chain = TargetChainCalculator.compute(
            capital = BigDecimal("100000"),
            targets = Targets(emptyList(), emptyList(), emptyList(), emptyList()),
        )

        chain.classes.map { it.assetClass } shouldBe AssetClass.entries.toList()
        val fixedIncome = chain.classes.filterIsInstance<FixedIncomeChain>().single()
        fixedIncome.subClasses.map { it.subClass } shouldBe FixedIncomeSubClass.entries.toList()
    }
})
