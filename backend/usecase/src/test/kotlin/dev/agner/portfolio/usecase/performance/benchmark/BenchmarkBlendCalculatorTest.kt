package dev.agner.portfolio.usecase.performance.benchmark

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.SubClassTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class BenchmarkBlendCalculatorTest : StringSpec({

    "weights each class benchmark by its target and normalises a whole policy" {
        val targets = Targets(
            classes = listOf(
                ClassTarget(AssetClass.ACOES, BigDecimal("60")),
                ClassTarget(AssetClass.FIIS, BigDecimal("10")),
                ClassTarget(AssetClass.RENDA_FIXA, BigDecimal("30")),
            ),
            strategies = emptyList(),
            subClasses = listOf(
                SubClassTarget(FixedIncomeSubClass.FLOATING_RATE, BigDecimal("70")),
                SubClassTarget(FixedIncomeSubClass.INFLATION_LINKED, BigDecimal("30")),
            ),
            tickers = emptyList(),
        )

        val blend = BenchmarkBlendCalculator.compute(
            targets = targets,
            returns = mapOf(
                BenchmarkIndex.IBOV to BigDecimal("2"),
                BenchmarkIndex.CDI to BigDecimal("1"),
                BenchmarkIndex.IPCA to BigDecimal("0.5"),
            ),
        )

        // (60*2 + 10*2 + 30*(0.7*1 + 0.3*0.5)) / 100 = 1.655
        blend.returnPercentage?.compareTo(BigDecimal("1.6550")) shouldBe 0
        blend.contributions.map { it.label } shouldBe listOf(
            "ACOES",
            "FIIS",
            "RENDA_FIXA.FLOATING_RATE",
            "RENDA_FIXA.INFLATION_LINKED",
        )
    }

    "has no blended total when a targeted leg has no benchmark" {
        val targets = Targets(
            classes = listOf(
                ClassTarget(AssetClass.ACOES, BigDecimal("50")),
                ClassTarget(AssetClass.RENDA_FIXA, BigDecimal("50")),
            ),
            strategies = emptyList(),
            subClasses = listOf(
                SubClassTarget(FixedIncomeSubClass.FIXED_RATE, BigDecimal("100")),
            ),
            tickers = emptyList(),
        )

        val blend = BenchmarkBlendCalculator.compute(
            targets = targets,
            returns = mapOf(BenchmarkIndex.IBOV to BigDecimal("2"), BenchmarkIndex.CDI to null),
        )

        blend.returnPercentage shouldBe null
        blend.contributions.single { it.label == "RENDA_FIXA.FIXED_RATE" }.returnPercentage shouldBe null
        blend.contributions.filter { it.target.signum() > 0 && it.returnPercentage == null }.map { it.label } shouldBe
            listOf("RENDA_FIXA.FIXED_RATE")
    }

    "a zero-target class with no benchmark does not block the total" {
        val targets = Targets(
            classes = listOf(
                ClassTarget(AssetClass.ACOES, BigDecimal("0")),
                ClassTarget(AssetClass.RENDA_FIXA, BigDecimal("100")),
            ),
            strategies = emptyList(),
            subClasses = listOf(SubClassTarget(FixedIncomeSubClass.FLOATING_RATE, BigDecimal("100"))),
            tickers = emptyList(),
        )

        val blend = BenchmarkBlendCalculator.compute(
            targets = targets,
            returns = mapOf(BenchmarkIndex.IBOV to null, BenchmarkIndex.CDI to BigDecimal("1")),
        )

        blend.returnPercentage?.compareTo(BigDecimal("1.0000")) shouldBe 0
    }

    "a manual Strategy adds no blend contribution or denominator weight" {
        val targets = Targets(
            classes = listOf(
                ClassTarget(AssetClass.ACOES, BigDecimal("50")),
                ClassTarget(AssetClass.RENDA_FIXA, BigDecimal("50")),
            ),
            strategies = listOf(StrategyTarget(AssetClass.ACOES, "Legado", BigDecimal("100"))),
            subClasses = listOf(
                SubClassTarget(FixedIncomeSubClass.FLOATING_RATE, BigDecimal("100")),
            ),
            tickers = emptyList(),
        )

        val blend = BenchmarkBlendCalculator.compute(
            targets = targets,
            returns = mapOf(BenchmarkIndex.IBOV to BigDecimal("2"), BenchmarkIndex.CDI to BigDecimal("1")),
        )

        // (50*2 + 50*1) / 100 = 1.5 — the bucket's 100% strategy share is not a blend weight.
        blend.returnPercentage?.compareTo(BigDecimal("1.5000")) shouldBe 0
        blend.contributions.map { it.label } shouldBe listOf("ACOES", "RENDA_FIXA.FLOATING_RATE")
    }

    "has no blended return without any priced benchmark" {
        val blend = BenchmarkBlendCalculator.compute(
            targets = Targets(
                classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("100"))),
                strategies = emptyList(),
                subClasses = emptyList(),
                tickers = emptyList(),
            ),
            returns = mapOf(BenchmarkIndex.IBOV to null),
        )

        blend.returnPercentage shouldBe null
    }
})
