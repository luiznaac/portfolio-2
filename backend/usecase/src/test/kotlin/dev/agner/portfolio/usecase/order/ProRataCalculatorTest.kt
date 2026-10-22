package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.order.model.FundingSummary
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.plain
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

// The last resort of the funding cascade: when the Uninvested Balance and every possible funding sell
// still leave a buy short, the buys are scaled by the same fraction of their demand (D5 worked example
// D). Scaling floors to whole units, so the rounding remainder stays unfunded rather than becoming a
// fractional order. A NEUTRO slice never reaches this seam — it emits no buy candidate at all.
//
// Example D rounds its deficits to 1,678 while the two deficits it lists (603 + 1,074) sum to 1,677;
// the test pins the exact demand, so the factor is 1,112 ÷ 1,677 = 0.6631 rather than the rounded
// 0.6627 the prose quotes.
class ProRataCalculatorTest : StringSpec({

    "scales every buy by the deployable share of the demand (D5 example D)" {
        val outcome = ProRataCalculator.compute(
            candidates = listOf(
                buy("VALE3", quantity = "603", price = "1"),
                buy("MXRF11", quantity = "1074", price = "1"),
            ),
            funding = summary(buyAmount = "1677", unfunded = "565"),
        )

        outcome.candidates.map { it.quantity.plain() } shouldBe listOf("399", "712")
        outcome.summary.originalBuyAmount.plain() shouldBe "1677"
        outcome.summary.factor.plain() shouldBe "0.6631"
        outcome.summary.buyAmount.plain() shouldBe "1111"
        // What the whole-unit floors could not deploy stays unfunded.
        outcome.summary.unfundedAmount.plain() shouldBe "566"
    }

    "leaves the buys untouched when the funding pass covers them" {
        val outcome = ProRataCalculator.compute(
            candidates = listOf(buy("VALE3", quantity = "10", price = "100")),
            funding = summary(buyAmount = "1000", unfunded = "0"),
        )

        outcome.candidates.single().quantity.plain() shouldBe "10"
        outcome.summary.factor.plain() shouldBe "1"
        outcome.summary.buyAmount.plain() shouldBe "1000"
        outcome.summary.unfundedAmount.plain() shouldBe "0"
    }

    "drops a buy that scales below one whole unit" {
        val outcome = ProRataCalculator.compute(
            candidates = listOf(
                buy("VALE3", quantity = "1", price = "1000"),
                buy("ITUB4", quantity = "10", price = "100"),
            ),
            funding = summary(buyAmount = "2000", unfunded = "1500"),
        )

        outcome.candidates.map { it.ticker to it.quantity.plain() } shouldBe listOf("ITUB4" to "2")
        outcome.summary.factor.plain() shouldBe "0.25"
        outcome.summary.buyAmount.plain() shouldBe "200"
        outcome.summary.unfundedAmount.plain() shouldBe "1800"
    }
})

private fun summary(buyAmount: String, unfunded: String) = FundingSummary(
    buyAmount = BigDecimal(buyAmount),
    sellAmount = BigDecimal.ZERO,
    unfundedAmount = BigDecimal(unfunded),
    neutroDeficit = BigDecimal.ZERO,
)

private fun buy(ticker: String, quantity: String, price: String) = OrderCandidate.traded(
    side = OrderSide.BUY,
    ticker = ticker,
    quantity = BigDecimal(quantity),
    price = BigDecimal(price),
    ideal = BigDecimal.ZERO,
    targetQuantity = BigDecimal.ZERO,
    position = BigDecimal.ZERO,
)
