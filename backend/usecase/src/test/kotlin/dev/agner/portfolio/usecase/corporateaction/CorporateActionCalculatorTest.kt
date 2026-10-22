package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.bd
import dev.agner.portfolio.usecase.corporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.listedasset.model.Position
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class CorporateActionCalculatorTest : StringSpec({

    "a desdobramento multiplies the quantity and divides the average price" {
        val position = CorporateActionCalculator.apply(position(quantity = "100", average = "10"), split("2"))

        position.quantity.compareTo(bd("200")) shouldBe 0
        position.averagePrice.compareTo(bd("5")) shouldBe 0
    }

    "a desdobramento keeps the total cost unchanged" {
        val before = position(quantity = "100", average = "10")
        val after = CorporateActionCalculator.apply(before, split("1.1"))

        after.quantity.compareTo(bd("110")) shouldBe 0
        after.averagePrice.compareTo(bd("9.090909")) shouldBe 0
        (before.quantity * before.averagePrice).subtract(after.quantity * after.averagePrice).abs()
            .compareTo(bd("0.01")) shouldBe -1
    }

    "a grupamento divides the quantity with a floor and multiplies the average price" {
        val position = CorporateActionCalculator.apply(position(quantity = "105", average = "10"), group("10"))

        position.quantity.compareTo(bd("10")) shouldBe 0
        position.averagePrice.compareTo(bd("100")) shouldBe 0
    }

    "a grupamento turns the leftover fraction into cash at the prior average price" {
        val action = group("10")

        requireNotNull(CorporateActionCalculator.cashAmount(position(quantity = "105", average = "10"), action))
            .compareTo(bd("50")) shouldBe 0
    }

    "a grupamento with no fraction pays no cash" {
        CorporateActionCalculator.cashAmount(position(quantity = "100", average = "10"), group("10"))
            .shouldBeNull()
    }

    "a bonificacao adds the declared value of the granted shares to the cost basis" {
        val action = bonus(ratio = "0.1", declaredValue = "2")

        val position = CorporateActionCalculator.apply(position(quantity = "100", average = "10"), action)

        position.quantity.compareTo(bd("110")) shouldBe 0
        position.averagePrice.compareTo(bd("10.909091")) shouldBe 0
    }

    "a bonificacao without a declared value only dilutes the average" {
        val position = CorporateActionCalculator.apply(
            position(quantity = "100", average = "10"),
            bonus(ratio = "1.0", declaredValue = null),
        )

        position.quantity.compareTo(bd("200")) shouldBe 0
        position.averagePrice.compareTo(bd("5")) shouldBe 0
    }

    "a restituicao reduces the cost basis by the cash received and keeps the quantity" {
        val action = cashOnly(CorporateActionKind.CAPITAL_RETURN, perShare = "1.5")

        val position = CorporateActionCalculator.apply(position(quantity = "100", average = "10"), action)

        position.quantity.compareTo(bd("100")) shouldBe 0
        position.averagePrice.compareTo(bd("8.5")) shouldBe 0
        requireNotNull(CorporateActionCalculator.cashAmount(position(quantity = "100", average = "10"), action))
            .compareTo(bd("150")) shouldBe 0
    }

    "a restituicao larger than the cost basis floors the average at zero" {
        val position = CorporateActionCalculator.apply(
            position(quantity = "100", average = "1"),
            cashOnly(CorporateActionKind.CAPITAL_RETURN, perShare = "2"),
        )

        position.averagePrice.compareTo(bd("0")) shouldBe 0
    }

    "an amortizacao pays cash without touching quantity or average" {
        val action = cashOnly(CorporateActionKind.AMORTIZATION, perShare = "0.8")
        val before = position(quantity = "100", average = "10")

        CorporateActionCalculator.apply(before, action) shouldBe before
        requireNotNull(CorporateActionCalculator.cashAmount(before, action)).compareTo(bd("80")) shouldBe 0
    }

    "a cash bonus pays cash without touching quantity or average" {
        val action = cashOnly(CorporateActionKind.CASH_BONUS, perShare = "0.25")
        val before = position(quantity = "100", average = "10")

        CorporateActionCalculator.apply(before, action) shouldBe before
        requireNotNull(CorporateActionCalculator.cashAmount(before, action)).compareTo(bd("25")) shouldBe 0
    }

    "a ticker change has no quantity, cost or cash effect" {
        val before = position(quantity = "100", average = "10")
        val action = action(CorporateActionKind.TICKER_CHANGE, targetTicker = "VALE3")

        CorporateActionCalculator.apply(before, action) shouldBe before
        CorporateActionCalculator.cashAmount(before, action).shouldBeNull()
    }

    "a review kind is never an auto-math event" {
        val before = position(quantity = "100", average = "10")

        CorporateActionCalculator.apply(before, action(CorporateActionKind.RIGHTS_ISSUE)) shouldBe before
    }

    "attribution slices scale by the event factor, with the grupamento floored" {
        CorporateActionCalculator.scaleQuantity(bd("30"), split("2")).compareTo(bd("60")) shouldBe 0
        CorporateActionCalculator.scaleQuantity(bd("30"), bonus(ratio = "0.1", declaredValue = null))
            .compareTo(bd("33")) shouldBe 0
        CorporateActionCalculator.scaleQuantity(bd("35"), group("10")).compareTo(bd("3")) shouldBe 0
        CorporateActionCalculator.scaleQuantity(bd("30"), action(CorporateActionKind.TICKER_CHANGE))
            .compareTo(bd("30")) shouldBe 0
    }
})

private fun position(quantity: String, average: String) = Position("PETR4", bd(quantity), bd(average))

private fun action(
    kind: CorporateActionKind,
    ratio: String? = null,
    declaredValue: String? = null,
    targetTicker: String? = null,
) = corporateAction(kind, ratio = ratio, declaredValuePerShare = declaredValue, targetTicker = targetTicker)

private fun split(ratio: String) = action(CorporateActionKind.SPLIT, ratio = ratio)

private fun group(ratio: String) = action(CorporateActionKind.REVERSE_SPLIT, ratio = ratio)

private fun bonus(ratio: String, declaredValue: String?) =
    action(CorporateActionKind.BONUS_SHARES, ratio = ratio, declaredValue = declaredValue)

private fun cashOnly(kind: CorporateActionKind, perShare: String) = action(kind, declaredValue = perShare)
