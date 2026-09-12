package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.order.model.TransferSuggestion
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class TransferMatcherTest : StringSpec({
    val matcher = TransferMatcher()

    "should propose moving the excess of one strategy into the shortage of another" {
        val deltas = mapOf(1 to BigDecimal("9"), 2 to BigDecimal("-9"))

        val suggestions = matcher.match(1, "ORVR3", deltas)

        suggestions shouldBe listOf(
            TransferSuggestion(
                listedAssetId = 1,
                ticker = "ORVR3",
                fromStrategyId = 1,
                toStrategyId = 2,
                quantity = BigDecimal("9"),
            ),
        )
    }

    "should split one strategy's excess across two strategies with shortage" {
        val deltas = mapOf(1 to BigDecimal("10"), 2 to BigDecimal("-6"), 3 to BigDecimal("-4"))

        val suggestions = matcher.match(1, "ITUB4", deltas)

        suggestions.map { it.toStrategyId to it.quantity } shouldBe listOf(2 to BigDecimal("6"), 3 to BigDecimal("4"))
        suggestions.all { it.fromStrategyId == 1 } shouldBe true
    }

    "should propose nothing when every strategy is already at its ideal" {
        val deltas = mapOf(1 to BigDecimal.ZERO, 2 to BigDecimal.ZERO)

        matcher.match(1, "PETR4", deltas) shouldBe emptyList()
    }

    "should propose nothing when everyone is short (no excess to move)" {
        val deltas = mapOf(1 to BigDecimal("-5"), 2 to BigDecimal("-3"))

        matcher.match(1, "VALE3", deltas) shouldBe emptyList()
    }

    "should only move the smaller of a mismatched excess/shortage pair, leaving a residual" {
        val deltas = mapOf(1 to BigDecimal("20"), 2 to BigDecimal("-5"))

        val suggestions = matcher.match(1, "PETR4", deltas)

        suggestions.single().quantity shouldBe BigDecimal("5")
    }

    "should conserve the total across suggestions — every unit taken is a unit given" {
        val deltas = mapOf(1 to BigDecimal("7"), 2 to BigDecimal("5"), 3 to BigDecimal("-9"))

        val suggestions = matcher.match(1, "VALE3", deltas)

        val moved = suggestions.sumOf { it.quantity }
        moved shouldBe BigDecimal("9")
        // One side runs dry: 7 + 5 = 12 of excess against 9 of shortage, so 3 stays excess and no
        // suggestion may exceed what the donor actually had.
        suggestions.filter { it.fromStrategyId == 1 }.sumOf { it.quantity } shouldBe BigDecimal("7")
        suggestions.all { it.quantity > BigDecimal.ZERO } shouldBe true
    }

    "should drain every strategy when excess and shortage are exact mirror images" {
        val deltas = mapOf(1 to BigDecimal("6"), 2 to BigDecimal("4"), 3 to BigDecimal("-4"), 4 to BigDecimal("-6"))

        val suggestions = matcher.match(1, "ITSA4", deltas)

        suggestions.sumOf { it.quantity } shouldBe BigDecimal("10")
        // Largest donor pairs with largest receiver: strategy 1's +6 goes to strategy 4's −6, and
        // strategy 2's +4 to strategy 3's −4. Accrued per strategy as it applies — donating
        // subtracts, receiving adds — every delta is worked off exactly, so the ticker needs no
        // trade at all once the transfers are booked.
        suggestions.map { Triple(it.fromStrategyId, it.toStrategyId, it.quantity) } shouldBe listOf(
            Triple(1, 4, BigDecimal("6")),
            Triple(2, 3, BigDecimal("4")),
        )
        val applied = suggestions
            .flatMap { listOf(it.fromStrategyId to -it.quantity, it.toStrategyId to it.quantity) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, moves) -> moves.sumOf { it } }
        applied shouldBe mapOf(
            1 to BigDecimal("-6"),
            2 to BigDecimal("-4"),
            3 to BigDecimal("4"),
            4 to BigDecimal("6"),
        )
    }

    "should ignore a zero delta when pairing" {
        val deltas = mapOf(1 to BigDecimal("5"), 2 to BigDecimal("0"), 3 to BigDecimal("-5"))

        val suggestions = matcher.match(1, "PETR4", deltas)

        suggestions.single().fromStrategyId shouldBe 1
        suggestions.single().toStrategyId shouldBe 3
    }
})
