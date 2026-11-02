package dev.agner.portfolio.usecase.performance

import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.income.model.Income
import dev.agner.portfolio.usecase.income.model.IncomeStatus
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.model.Provenance
import dev.agner.portfolio.usecase.income.model.TaxNature
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.sell
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class ComputedPerformanceCalculatorTest : StringSpec({

    "walks every month from the earliest ledger row to the month before the first close" {
        val months = ComputedPerformanceCalculator.window(
            earliestLedgerMonth = YearMonth.parse("2025-09"),
            firstSnapshotMonth = YearMonth.parse("2026-01"),
            lastCompletedMonth = YearMonth.parse("2026-10"),
        )

        months shouldBe listOf("2025-09", "2025-10", "2025-11", "2025-12").map { YearMonth.parse(it) }
    }

    "runs to the last completed calendar month while no month was closed" {
        val months = ComputedPerformanceCalculator.window(
            earliestLedgerMonth = YearMonth.parse("2026-08"),
            firstSnapshotMonth = null,
            lastCompletedMonth = YearMonth.parse("2026-10"),
        )

        months shouldBe listOf("2026-08", "2026-09", "2026-10").map { YearMonth.parse(it) }
    }

    "has no computed month when the ledger starts after the first close" {
        ComputedPerformanceCalculator.window(
            earliestLedgerMonth = YearMonth.parse("2026-02"),
            firstSnapshotMonth = YearMonth.parse("2026-01"),
            lastCompletedMonth = YearMonth.parse("2026-10"),
        ).shouldBeEmpty()
    }

    "has no computed month without a ledger row" {
        ComputedPerformanceCalculator.window(
            earliestLedgerMonth = null,
            firstSnapshotMonth = null,
            lastCompletedMonth = YearMonth.parse("2026-10"),
        ).shouldBeEmpty()
    }

    "nets fees into the month's aportes and subtracts the income" {
        val aportes = ComputedPerformanceCalculator.netAportes(
            trades = listOf(
                buy(id = 1, date = "2026-02-10", quantity = "10", unitPrice = "10", fee = "5"),
                sell(id = 2, date = "2026-02-20", quantity = "5", unitPrice = "12", fee = "2"),
            ),
            incomes = listOf(income(date = "2026-02-15", net = "30")),
            period = YearMonth.parse("2026-02"),
        )

        aportes.compareTo(BigDecimal("17")) shouldBe 0
    }

    "reads a sell-only month as a negative aporte" {
        val aportes = ComputedPerformanceCalculator.netAportes(
            trades = listOf(sell(id = 1, date = "2026-02-20", quantity = "10", unitPrice = "10")),
            incomes = emptyList(),
            period = YearMonth.parse("2026-02"),
        )

        aportes.compareTo(BigDecimal("-100")) shouldBe 0
    }

    "excludes an Opening Trade from the aportes and reads a quiet month as zero" {
        val opening = buy(
            id = 1,
            date = "2026-02-01",
            quantity = "100",
            unitPrice = "10",
            source = TradeSource.OPENING,
        )

        ComputedPerformanceCalculator.netAportes(
            trades = listOf(opening),
            incomes = emptyList(),
            period = YearMonth.parse("2026-02"),
        ).compareTo(BigDecimal.ZERO) shouldBe 0

        ComputedPerformanceCalculator.netAportes(
            trades = emptyList(),
            incomes = emptyList(),
            period = YearMonth.parse("2026-02"),
        ).compareTo(BigDecimal.ZERO) shouldBe 0
    }

    "prices the held positions and groups them by effective asset class" {
        val value = ComputedPerformanceCalculator.valueAt(
            positions = listOf(
                Position("PETR4", BigDecimal("10"), BigDecimal("0")),
                Position("HGLG11", BigDecimal("5"), BigDecimal("0")),
            ),
            closes = mapOf("PETR4" to BigDecimal("5"), "HGLG11" to BigDecimal("8")),
            assetClasses = mapOf("PETR4" to AssetClass.ACOES, "HGLG11" to AssetClass.FIIS),
        )

        value.value.compareTo(BigDecimal("90")) shouldBe 0
        value.partial shouldBe false
        value.classes.map { it.assetClass } shouldBe listOf(AssetClass.ACOES, AssetClass.FIIS)
        value.classes.first().valueEnd.compareTo(BigDecimal("50")) shouldBe 0
        value.classes.last().valueEnd.compareTo(BigDecimal("40")) shouldBe 0
    }

    "excludes an unpriced held ticker and marks the month partial" {
        val value = ComputedPerformanceCalculator.valueAt(
            positions = listOf(
                Position("PETR4", BigDecimal("10"), BigDecimal("0")),
                Position("VALE3", BigDecimal("5"), BigDecimal("0")),
            ),
            closes = mapOf("PETR4" to BigDecimal("5")),
            assetClasses = mapOf("PETR4" to AssetClass.ACOES, "VALE3" to AssetClass.ACOES),
        )

        value.value.compareTo(BigDecimal("50")) shouldBe 0
        value.partial shouldBe true
        value.classes.single().valueEnd.compareTo(BigDecimal("50")) shouldBe 0
    }

    "keeps a delisted ticker's last close instead of calling it unpriced" {
        val value = ComputedPerformanceCalculator.valueAt(
            positions = listOf(Position("DELIST3", BigDecimal("10"), BigDecimal("0"))),
            closes = mapOf("DELIST3" to BigDecimal("3")),
            assetClasses = mapOf("DELIST3" to AssetClass.ACOES),
        )

        value.value.compareTo(BigDecimal("30")) shouldBe 0
        value.partial shouldBe false
    }

    "does not mark a month partial for a zero-quantity ticker with no close" {
        val value = ComputedPerformanceCalculator.valueAt(
            positions = listOf(Position("PETR4", BigDecimal.ZERO, BigDecimal.ZERO)),
            closes = emptyMap(),
            assetClasses = mapOf("PETR4" to AssetClass.ACOES),
        )

        value.value.compareTo(BigDecimal.ZERO) shouldBe 0
        value.partial shouldBe false
        value.classes.shouldBeEmpty()
    }

    "counts an unclassified ticker in the portfolio total but in no class" {
        val value = ComputedPerformanceCalculator.valueAt(
            positions = listOf(
                Position("PETR4", BigDecimal("10"), BigDecimal("0")),
                Position("ZZZZ9", BigDecimal("5"), BigDecimal("0")),
            ),
            closes = mapOf("PETR4" to BigDecimal("5"), "ZZZZ9" to BigDecimal("2")),
            assetClasses = mapOf("PETR4" to AssetClass.ACOES, "ZZZZ9" to null),
        )

        value.value.compareTo(BigDecimal("60")) shouldBe 0
        value.classes.single().assetClass shouldBe AssetClass.ACOES
        value.classes.single().valueEnd.compareTo(BigDecimal("50")) shouldBe 0
    }
})

private fun income(date: String, net: String) = Income(
    id = 1,
    ticker = "PETR4",
    date = LocalDate.parse(date),
    type = IncomeType.DIVIDEND,
    grossAmount = BigDecimal(net),
    retainedAmount = BigDecimal.ZERO,
    netAmount = BigDecimal(net),
    taxNature = TaxNature.INCOME,
    provenance = Provenance.B3,
    status = IncomeStatus.RECEIVED,
)
