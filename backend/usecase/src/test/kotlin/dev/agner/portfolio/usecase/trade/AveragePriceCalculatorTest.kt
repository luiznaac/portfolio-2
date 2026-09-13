package dev.agner.portfolio.usecase.trade

import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.sell
import dev.agner.portfolio.usecase.trade.model.Position
import dev.agner.portfolio.usecase.trade.model.RealizedGain
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class AveragePriceCalculatorTest : StringSpec({
    val calculator = AveragePriceCalculator()

    "weights the average price across multiple buys" {
        val result = calculator.calculate(
            trades = listOf(
                buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "10", price = "10"),
                buy(id = 2, date = LocalDate(2026, 1, 10), quantity = "10", price = "20"),
            ),
            corporateActions = emptyList(),
        )

        result.position shouldBe Position(
            quantity = BigDecimal("20"),
            averagePrice = BigDecimal("15.000000"),
            totalCost = BigDecimal("300.00"),
        )
        result.realizedGains shouldBe emptyList()
    }

    "uses the average price at sale time, not FIFO, for the cost basis" {
        val result = calculator.calculate(
            trades = listOf(
                buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "10", price = "10"),
                buy(id = 2, date = LocalDate(2026, 1, 10), quantity = "10", price = "20"),
                sell(id = 3, date = LocalDate(2026, 2, 1), quantity = "5", price = "25"),
            ),
            corporateActions = emptyList(),
        )

        result.position shouldBe Position(
            quantity = BigDecimal("15"),
            averagePrice = BigDecimal("15.000000"),
            totalCost = BigDecimal("225.00"),
        )
        result.realizedGains.single() shouldBe RealizedGain(
            tradeId = 3,
            date = LocalDate(2026, 2, 1),
            quantity = BigDecimal("5"),
            proceeds = BigDecimal("125.00"),
            costBasis = BigDecimal("75.00"),
        )
        result.realizedGains.single().gain shouldBe BigDecimal("50.00")
    }

    "rounds a half-cent cost basis down to the even digit at scale 2" {
        val result = calculator.calculate(
            trades = listOf(
                buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "2000", price = "1.0005"),
                sell(id = 2, date = LocalDate(2026, 1, 10), quantity = "10", price = "2"),
            ),
            corporateActions = emptyList(),
        )

        // The average is exactly 1.000500, so the ten shares sold cost 10.005000: HALF_EVEN rounds
        // to 10.00 (the even digit), while HALF_UP would give 10.01.
        result.realizedGains.single().costBasis shouldBe BigDecimal("10.00")
        result.realizedGains.single().proceeds shouldBe BigDecimal("20.00")
        result.position shouldBe Position(
            quantity = BigDecimal("1990"),
            averagePrice = BigDecimal("1.000503"),
            totalCost = BigDecimal("1991.00"),
        )
    }

    "rounds a half-cent cost basis up to the even digit at scale 2" {
        val result = calculator.calculate(
            trades = listOf(
                buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "2000", price = "1.0015"),
                sell(id = 2, date = LocalDate(2026, 1, 10), quantity = "10", price = "2"),
            ),
            corporateActions = emptyList(),
        )

        // The average is exactly 1.001500, so the ten shares sold cost 10.015000: HALF_EVEN rounds
        // to 10.02 (the even digit), while HALF_DOWN would give 10.01.
        result.realizedGains.single().costBasis shouldBe BigDecimal("10.02")
        result.realizedGains.single().proceeds shouldBe BigDecimal("20.00")
        result.position shouldBe Position(
            quantity = BigDecimal("1990"),
            averagePrice = BigDecimal("1.001497"),
            totalCost = BigDecimal("1992.98"),
        )
    }

    "rounds a recurring-decimal cost basis down at scale 2" {
        val result = calculator.calculate(
            trades = listOf(
                buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "1", price = "10"),
                buy(id = 2, date = LocalDate(2026, 1, 6), quantity = "2", price = "11"),
                sell(id = 3, date = LocalDate(2026, 1, 7), quantity = "3", price = "12"),
            ),
            corporateActions = emptyList(),
        )

        // The average is 10.666667, so three shares cost 32.000001 and round down to 32.00.
        result.realizedGains.single().costBasis shouldBe BigDecimal("32.00")
        result.position shouldBe Position(
            quantity = BigDecimal("0"),
            averagePrice = BigDecimal("0"),
            totalCost = BigDecimal("0.00"),
        )
    }

    "resets the average on a full exit and starts fresh on the next buy" {
        val result = calculator.calculate(
            trades = listOf(
                buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "10", price = "10"),
                sell(id = 2, date = LocalDate(2026, 1, 10), quantity = "10", price = "20"),
                buy(id = 3, date = LocalDate(2026, 2, 1), quantity = "5", price = "30"),
            ),
            corporateActions = emptyList(),
        )

        result.position shouldBe Position(
            quantity = BigDecimal("5"),
            averagePrice = BigDecimal("30.000000"),
            totalCost = BigDecimal("150.00"),
        )
    }

    "sorts the timeline by date regardless of input order" {
        val chronological = calculator.calculate(
            trades = listOf(
                buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "10", price = "10"),
                sell(id = 2, date = LocalDate(2026, 1, 10), quantity = "5", price = "20"),
            ),
            corporateActions = emptyList(),
        )
        val shuffled = calculator.calculate(
            trades = listOf(
                sell(id = 2, date = LocalDate(2026, 1, 10), quantity = "5", price = "20"),
                buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "10", price = "10"),
            ),
            corporateActions = emptyList(),
        )

        shuffled shouldBe chronological
    }

    "applies a same-day corporate action before the trade" {
        val result = calculator.calculate(
            trades = listOf(
                buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "10", price = "10"),
                buy(id = 2, date = LocalDate(2026, 1, 10), quantity = "10", price = "20"),
            ),
            corporateActions = listOf(
                CorporateAction.Split(
                    id = 100,
                    assetId = 1,
                    date = LocalDate(2026, 1, 10),
                    ratio = BigDecimal("2"),
                ),
            ),
        )

        // Applying the split first doubles the 10 shares held before that day's buy, which then
        // adds 10: 30 shares carrying 300.00. The opposite order would have produced 40 shares.
        result.position shouldBe Position(
            quantity = BigDecimal("30"),
            averagePrice = BigDecimal("10.000000"),
            totalCost = BigDecimal("300.00"),
        )
    }

    "keeps total cost and multiplies quantity on a split" {
        val result = calculator.calculate(
            trades = listOf(buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "10", price = "10")),
            corporateActions = listOf(
                CorporateAction.Split(
                    id = 100,
                    assetId = 1,
                    date = LocalDate(2026, 2, 1),
                    ratio = BigDecimal("2"),
                ),
            ),
        )

        result.position shouldBe Position(
            quantity = BigDecimal("20"),
            averagePrice = BigDecimal("5.000000"),
            totalCost = BigDecimal("100.00"),
        )
    }

    "divides quantity at scale 8 on a reverse split" {
        val result = calculator.calculate(
            trades = listOf(buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "10", price = "10")),
            corporateActions = listOf(
                CorporateAction.ReverseSplit(
                    id = 100,
                    assetId = 1,
                    date = LocalDate(2026, 2, 1),
                    ratio = BigDecimal("3"),
                ),
            ),
        )

        result.position shouldBe Position(
            quantity = BigDecimal("3.33333333"),
            averagePrice = BigDecimal("30.000000"),
            totalCost = BigDecimal("100.00"),
        )
    }

    "adds bonus shares at the declared value and raises total cost" {
        val result = calculator.calculate(
            trades = listOf(buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "10", price = "10")),
            corporateActions = listOf(
                CorporateAction.Bonus(
                    id = 100,
                    assetId = 1,
                    date = LocalDate(2026, 2, 1),
                    ratio = BigDecimal("1"),
                    valuePerNewShare = BigDecimal("2"),
                ),
            ),
        )

        // 10 new shares at 2.00 each: 20 shares carrying the original 100.00 plus 20.00.
        result.position shouldBe Position(
            quantity = BigDecimal("20"),
            averagePrice = BigDecimal("6.000000"),
            totalCost = BigDecimal("120.00"),
        )
    }

    "changes nothing numerically on a ticker change" {
        val result = calculator.calculate(
            trades = listOf(buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "10", price = "10")),
            corporateActions = listOf(
                CorporateAction.TickerChange(
                    id = 100,
                    assetId = 1,
                    date = LocalDate(2026, 2, 1),
                    newTicker = "NEW3",
                ),
            ),
        )

        result.position shouldBe Position(
            quantity = BigDecimal("10"),
            averagePrice = BigDecimal("10.000000"),
            totalCost = BigDecimal("100.00"),
        )
    }

    "emits one realized gain per sell in timeline order" {
        val result = calculator.calculate(
            trades = listOf(
                buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "10", price = "10"),
                sell(id = 3, date = LocalDate(2026, 3, 1), quantity = "4", price = "5"),
                sell(id = 2, date = LocalDate(2026, 2, 1), quantity = "2", price = "30"),
            ),
            corporateActions = emptyList(),
        )

        result.realizedGains shouldBe listOf(
            RealizedGain(
                tradeId = 2,
                date = LocalDate(2026, 2, 1),
                quantity = BigDecimal("2"),
                proceeds = BigDecimal("60.00"),
                costBasis = BigDecimal("20.00"),
            ),
            RealizedGain(
                tradeId = 3,
                date = LocalDate(2026, 3, 1),
                quantity = BigDecimal("4"),
                proceeds = BigDecimal("20.00"),
                costBasis = BigDecimal("40.00"),
            ),
        )
    }

    "returns a zero position and no gains for empty input" {
        val result = calculator.calculate(trades = emptyList(), corporateActions = emptyList())

        result.position shouldBe Position(
            quantity = BigDecimal("0"),
            averagePrice = BigDecimal("0"),
            totalCost = BigDecimal("0.00"),
        )
        result.realizedGains shouldBe emptyList()
    }

    "pins current oversell behavior — open question, not a spec" {
        val result = calculator.calculate(
            trades = listOf(
                buy(id = 1, date = LocalDate(2026, 1, 5), quantity = "5", price = "10"),
                sell(id = 2, date = LocalDate(2026, 1, 10), quantity = "10", price = "12"),
            ),
            corporateActions = emptyList(),
        )

        // Today a sell larger than the position just goes negative: -5 shares, -50.00 total cost.
        // This characterizes what the code does, not what it should do — whether an oversell should
        // throw, be rejected upstream or net out is an open question.
        result.position shouldBe Position(
            quantity = BigDecimal("-5"),
            averagePrice = BigDecimal("10.000000"),
            totalCost = BigDecimal("-50.00"),
        )
        result.realizedGains.single().costBasis shouldBe BigDecimal("100.00")
    }
})
