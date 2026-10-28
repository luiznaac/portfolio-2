package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.corporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.sell
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class PositionCalculatorTest : StringSpec({

    "an empty ledger replays to an empty position" {
        PositionCalculator.replay("PETR4", emptyList()) shouldBe Position("PETR4", bd("0"), bd("0"))
    }

    "a buy sets quantity and the average to its unit price" {
        val position = PositionCalculator.replay("PETR4", listOf(buy(quantity = "100", unitPrice = "10")))

        position.quantity.compareTo(bd("100")) shouldBe 0
        position.averagePrice.compareTo(bd("10")) shouldBe 0
    }

    "a second buy re-weights the average by the units it adds" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(buy(quantity = "100", unitPrice = "10"), buy(id = 2, quantity = "100", unitPrice = "20")),
        )

        position.quantity.compareTo(bd("200")) shouldBe 0
        position.averagePrice.compareTo(bd("15")) shouldBe 0
    }

    "a sell removes units and leaves the average untouched" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(buy(quantity = "100", unitPrice = "10"), sell(id = 2, quantity = "40", unitPrice = "12")),
        )

        position.quantity.compareTo(bd("60")) shouldBe 0
        position.averagePrice.compareTo(bd("10")) shouldBe 0
    }

    "selling the whole position resets the average" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(buy(quantity = "100", unitPrice = "10"), sell(id = 2, quantity = "100", unitPrice = "12")),
        )

        position.quantity.compareTo(bd("0")) shouldBe 0
        position.averagePrice.compareTo(bd("0")) shouldBe 0
    }

    "an over-sell keeps the signed quantity and resets the average" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(buy(quantity = "100", unitPrice = "10"), sell(id = 2, quantity = "150", unitPrice = "12")),
        )

        position.quantity.compareTo(bd("-50")) shouldBe 0
        position.averagePrice.compareTo(bd("0")) shouldBe 0
    }

    "a buy that exactly offsets an over-sell leaves zero quantity and resets the average" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(
                buy(quantity = "100", unitPrice = "10"),
                sell(id = 2, quantity = "150", unitPrice = "12"),
                buy(id = 3, quantity = "50", unitPrice = "20"),
            ),
        )

        position.quantity.compareTo(bd("0")) shouldBe 0
        position.averagePrice.compareTo(bd("0")) shouldBe 0
    }

    "replay is ordered by date then id, not by input order" {
        val later = buy(id = 1, date = "2026-10-20", quantity = "100", unitPrice = "20")
        val earlier = buy(id = 2, date = "2026-10-10", quantity = "100", unitPrice = "10")

        val position = PositionCalculator.replay("PETR4", listOf(later, earlier))

        position.averagePrice.compareTo(bd("15")) shouldBe 0
    }

    "a repeated same-day tuple is applied in id order" {
        val first = buy(id = 1, quantity = "100", unitPrice = "10")
        val second = buy(id = 2, quantity = "100", unitPrice = "20")

        PositionCalculator.replay("PETR4", listOf(second, first)).averagePrice.compareTo(bd("15")) shouldBe 0
    }

    "the average is rounded to the stored scale" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(buy(quantity = "1", unitPrice = "1"), buy(id = 2, quantity = "2", unitPrice = "2")),
        )

        position.averagePrice.compareTo(bd("1.666667")) shouldBe 0
        position.averagePrice.scale() shouldBe 6
    }

    "the position before a sale is the basis that sale's gain is measured on" {
        val sell = sell(id = 2, quantity = "40", unitPrice = "12")

        val position = PositionCalculator.positionBefore(
            "PETR4",
            listOf(buy(quantity = "100", unitPrice = "10"), sell),
            emptyList(),
            sell,
        )

        position.quantity.compareTo(bd("100")) shouldBe 0
        position.averagePrice.compareTo(bd("10")) shouldBe 0
    }

    "the position before a trade excludes every later trade" {
        val second = buy(id = 2, quantity = "100", unitPrice = "20")

        val position = PositionCalculator.positionBefore(
            "PETR4",
            listOf(buy(quantity = "100", unitPrice = "10"), second),
            emptyList(),
            second,
        )

        position.quantity.compareTo(bd("100")) shouldBe 0
        position.averagePrice.compareTo(bd("10")) shouldBe 0
    }

    "an ex-date corporate action applies before a same-day trade" {
        val split = corporateAction(CorporateActionKind.SPLIT, exDate = "2026-10-14", ratio = "2")
        val sell = sell(id = 2, date = "2026-10-14", quantity = "50", unitPrice = "6")

        val position = PositionCalculator.positionBefore(
            "PETR4",
            listOf(buy(date = "2026-10-10", quantity = "100", unitPrice = "10"), sell),
            listOf(split),
            sell,
        )

        position.quantity.compareTo(bd("200")) shouldBe 0
        position.averagePrice.compareTo(bd("5")) shouldBe 0
    }
})

private fun bd(value: String) = BigDecimal(value)
