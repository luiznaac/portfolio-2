package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.order.model.AttributionSide
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.TickerOrderPlan
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

// The attribution planner, pinned on the rebalancing spec's worked example A (ITUB4): a BUY allocates
// to the most-below-ideal slice first, a SELL deallocates from the most-above-ideal first, and when
// two slices of the same ticker still sit ≥ 1 whole unit off-ideal the planner suggests a bookkeeping
// Attribution Transfer between them.
class AttributionPlannerTest : StringSpec({

    "allocates a buy to the most-below-ideal slice first and transfers the leftover unit (D5 example A)" {
        val outcome = plan(
            slices = slices(targets = listOf("38", "29", "19"), attributed = listOf("33", "23", "20")),
            candidates = listOf(buy(quantity = "10")),
        )

        outcome.moves.map { it.strategy to (it.side to it.quantity.plain()) } shouldBe listOf(
            "Dividendos" to (AttributionSide.ALLOCATE to "6"),
            "Top" to (AttributionSide.ALLOCATE to "4"),
        )
        outcome.transfers.map { (it.fromStrategy to it.toStrategy) to it.quantity.plain() } shouldBe
            listOf(("Dividendos Plus" to "Top") to "1")
        outcome.transfers.single().amount!!.plain() shouldBe "42.62"
    }

    "deallocates a sell from the most-above-ideal slice first" {
        val outcome = plan(
            slices = slices(targets = listOf("38", "29", "19"), attributed = listOf("33", "23", "20")),
            candidates = listOf(sell(quantity = "12")),
        )

        outcome.moves.map { it.strategy to (it.side to it.quantity.plain()) } shouldBe listOf(
            "Dividendos Plus" to (AttributionSide.DEALLOCATE to "1"),
            "Top" to (AttributionSide.DEALLOCATE to "5"),
            "Dividendos" to (AttributionSide.DEALLOCATE to "6"),
        )
    }

    "suggests a transfer whenever two slices sit a whole unit off-ideal, even without an order" {
        val outcome = plan(
            slices = slices(targets = listOf("38", "29", "19"), attributed = listOf("33", "23", "20")),
            candidates = emptyList(),
        )

        outcome.moves.shouldBeEmpty()
        outcome.transfers.map { (it.fromStrategy to it.toStrategy) to it.quantity.plain() } shouldBe
            listOf(("Dividendos Plus" to "Dividendos") to "1")
    }

    "never allocates a buy into a NEUTRO slice" {
        val outcome = plan(
            slices = listOf(
                slice(ticker = "VALE3", strategy = "Top", target = "50", attributed = "10"),
                slice(ticker = "VALE3", strategy = "Dividendos", target = "50", attributed = "40", neutro = true),
            ),
            candidates = listOf(buy(ticker = "VALE3", quantity = "30")),
        )

        outcome.moves.map { it.strategy to it.quantity.plain() } shouldBe listOf("Top" to "30")
        outcome.transfers.shouldBeEmpty()
    }

    "deallocates a NEUTRO sell from the slice that holds the units" {
        val outcome = plan(
            slices = listOf(
                slice(ticker = "B3SA3", strategy = "Dividendos", target = "100", attributed = "40", neutro = true),
            ),
            candidates = listOf(sell(ticker = "B3SA3", quantity = "40")),
        )

        outcome.moves.map { it.strategy to (it.side to it.quantity.plain()) } shouldBe listOf(
            "Dividendos" to (AttributionSide.DEALLOCATE to "40"),
        )
        outcome.transfers.shouldBeEmpty()
    }

    "moves bucket units to the largest-deficit report slice first and leaves the remainder in the bucket" {
        val outcome = plan(
            slices = listOf(
                slice(ticker = "ITUB4", strategy = "Top", target = "60", attributed = "40"),
                slice(ticker = "ITUB4", strategy = "Dividendos", target = "30", attributed = "20"),
            ),
            candidates = emptyList(),
            manual = mapOf("Legado" to "45"),
        )

        outcome.transfers.map { (it.fromStrategy to it.toStrategy) to it.quantity.plain() } shouldBe listOf(
            ("Legado" to "Top") to "20",
            ("Legado" to "Dividendos") to "10",
        )
    }

    "moves bucket units in whole units only" {
        val outcome = plan(
            slices = listOf(slice(ticker = "ITUB4", strategy = "Top", target = "60", attributed = "40")),
            candidates = emptyList(),
            manual = mapOf("Legado" to "20.9"),
        )

        outcome.transfers.map { (it.fromStrategy to it.toStrategy) to it.quantity.plain() } shouldBe
            listOf(("Legado" to "Top") to "20")
    }

    "proposes no bucket transfer when no report slice targets the ticker" {
        val outcome = plan(
            slices = emptyList(),
            candidates = emptyList(),
            manual = mapOf("Legado" to "50"),
        )

        outcome.transfers.shouldBeEmpty()
    }

    "prefers a report slice's surplus over the bucket when both can fund a deficit" {
        val outcome = plan(
            slices = listOf(
                slice(ticker = "ITUB4", strategy = "Top", target = "100", attributed = "120"),
                slice(ticker = "ITUB4", strategy = "Dividendos", target = "100", attributed = "60"),
            ),
            candidates = emptyList(),
            manual = mapOf("Legado" to "50"),
        )

        outcome.transfers.map { (it.fromStrategy to it.toStrategy) to it.quantity.plain() } shouldBe listOf(
            ("Top" to "Dividendos") to "20",
            ("Legado" to "Dividendos") to "20",
        )
    }
})

private fun plan(
    slices: List<TickerOrderPlan>,
    candidates: List<OrderCandidate>,
    manual: Map<String, String> = emptyMap(),
) = AttributionPlanner.compute(
    slices = slices,
    candidates = candidates,
    manualSlices = manual.map { (strategy, quantity) ->
        StrategyTicker(strategy, "ITUB4") to BigDecimal(quantity)
    }.toMap(),
)

private fun slices(targets: List<String>, attributed: List<String>) = listOf(
    slice(ticker = "ITUB4", strategy = "Top", target = targets[0], attributed = attributed[0]),
    slice(ticker = "ITUB4", strategy = "Dividendos", target = targets[1], attributed = attributed[1]),
    slice(ticker = "ITUB4", strategy = "Dividendos Plus", target = targets[2], attributed = attributed[2]),
)

private fun slice(
    ticker: String,
    strategy: String,
    target: String,
    attributed: String,
    neutro: Boolean = false,
) = TickerOrderPlan(
    ticker = ticker,
    strategy = strategy,
    target = BigDecimal(target),
    ideal = BigDecimal.ZERO,
    price = BigDecimal("42.62"),
    targetQuantity = BigDecimal(target),
    neutro = neutro,
    attributedQuantity = BigDecimal(attributed),
)

private fun buy(ticker: String = "ITUB4", quantity: String) = OrderCandidate.traded(
    side = OrderSide.BUY,
    ticker = ticker,
    quantity = BigDecimal(quantity),
    price = BigDecimal("42.62"),
    ideal = BigDecimal.ZERO,
    targetQuantity = BigDecimal.ZERO,
    position = BigDecimal.ZERO,
)

private fun sell(ticker: String = "ITUB4", quantity: String) = OrderCandidate.fundingSell(
    ticker = ticker,
    quantity = BigDecimal(quantity),
    price = BigDecimal("42.62"),
    ideal = BigDecimal.ZERO,
    targetQuantity = BigDecimal.ZERO,
    position = BigDecimal.ZERO,
    fundingSource = dev.agner.portfolio.usecase.order.model.FundingSource.SURPLUS,
)
