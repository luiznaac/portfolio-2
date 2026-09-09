package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.order.model.TransferSuggestion
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class TransferMatcherTest : StringSpec({
    val matcher = TransferMatcher()
    val names = mapOf(1 to "Top", 2 to "Dividendos", 3 to "Small Caps")

    "should propose moving the excess of one strategy into the shortage of another" {
        val deltas = mapOf(1 to BigDecimal("9"), 2 to BigDecimal("-9"))

        val suggestions = matcher.match(1, "ORVR3", deltas, names)

        suggestions shouldBe listOf(
            TransferSuggestion(
                listedAssetId = 1,
                ticker = "ORVR3",
                fromStrategyId = 1,
                fromStrategyName = "Top",
                toStrategyId = 2,
                toStrategyName = "Dividendos",
                quantity = BigDecimal("9"),
            ),
        )
    }

    "should split one strategy's excess across two strategies with shortage" {
        val deltas = mapOf(1 to BigDecimal("10"), 2 to BigDecimal("-6"), 3 to BigDecimal("-4"))

        val suggestions = matcher.match(1, "ITUB4", deltas, names)

        suggestions.map { it.toStrategyId to it.quantity } shouldBe listOf(2 to BigDecimal("6"), 3 to BigDecimal("4"))
        suggestions.all { it.fromStrategyId == 1 } shouldBe true
    }

    "should propose nothing when every strategy is already at its ideal" {
        val deltas = mapOf(1 to BigDecimal.ZERO, 2 to BigDecimal.ZERO)

        matcher.match(1, "PETR4", deltas, names) shouldBe emptyList()
    }

    "should propose nothing when everyone is short (no excess to move)" {
        val deltas = mapOf(1 to BigDecimal("-5"), 2 to BigDecimal("-3"))

        matcher.match(1, "VALE3", deltas, names) shouldBe emptyList()
    }

    "should only move the smaller of a mismatched excess/shortage pair, leaving a residual" {
        val deltas = mapOf(1 to BigDecimal("20"), 2 to BigDecimal("-5"))

        val suggestions = matcher.match(1, "PETR4", deltas, names)

        suggestions.single().quantity shouldBe BigDecimal("5")
    }
})
