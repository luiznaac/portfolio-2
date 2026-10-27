package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.order.model.FundingSource
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.TickerOrderPlan
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

// The funding cascade's arithmetic, pinned on the source order the specification fixes: NEUTRO
// slices, then positions outside every strategy, then the largest surpluses by R$. Sells exist only
// to cover what the Uninvested Balance above the cash Floor cannot, and only whole units are sold, so
// any remainder stays unfunded. No repository reaches this seam.
class FundingCalculatorTest : StringSpec({

    "sells nothing when the deployable cash already covers every buy" {
        val outcome = compute(
            candidates = listOf(buy("VALE3", quantity = "10", price = "100")),
            slices = listOf(slice("VALE3", targetQuantity = "10")),
            uninvestedBalance = "1000",
        )

        outcome.sells.shouldBeEmpty()
        outcome.summary.buyAmount.plain() shouldBe "1000"
        outcome.summary.sellAmount.plain() shouldBe "0"
        outcome.summary.unfundedAmount.plain() shouldBe "0"
    }

    "sells only enough of the largest surplus to meet the shortfall" {
        val outcome = compute(
            candidates = listOf(buy("VALE3", quantity = "50", price = "100")),
            slices = listOf(
                slice("VALE3", targetQuantity = "50"),
                slice("ITUB4", targetQuantity = "100"),
            ),
            positions = mapOf("ITUB4" to "120"),
            prices = mapOf("VALE3" to "100", "ITUB4" to "50"),
            uninvestedBalance = "4500",
        )

        outcome.sells.map { it.ticker to it.quantity.plain() } shouldBe listOf("ITUB4" to "10")
        outcome.sells.single().amount!!.plain() shouldBe "500"
        outcome.sells.single().source() shouldBe FundingSource.SURPLUS
        outcome.summary.unfundedAmount.plain() shouldBe "0"
    }

    "sells NEUTRO slices before the largest surplus" {
        val outcome = compute(
            candidates = listOf(buy("VALE3", quantity = "50", price = "100")),
            slices = listOf(
                slice("VALE3", targetQuantity = "50"),
                slice("B3SA3", targetQuantity = "200", neutro = true, attributed = "60"),
                slice("ITUB4", targetQuantity = "100"),
            ),
            positions = mapOf("B3SA3" to "60", "ITUB4" to "120"),
            prices = mapOf("VALE3" to "100", "B3SA3" to "25", "ITUB4" to "50"),
            uninvestedBalance = "3000",
        )

        outcome.sells.map { it.ticker to it.source() } shouldBe listOf(
            "B3SA3" to FundingSource.NEUTRO,
            "ITUB4" to FundingSource.SURPLUS,
        )
        outcome.sells.map { it.quantity.plain() } shouldBe listOf("60", "10")
        outcome.summary.sellAmount.plain() shouldBe "2000"
        outcome.summary.unfundedAmount.plain() shouldBe "0"
    }

    "sells a position outside every strategy before a targeted surplus" {
        val outcome = compute(
            candidates = listOf(buy("VALE3", quantity = "50", price = "100")),
            slices = listOf(
                slice("VALE3", targetQuantity = "50"),
                slice("ITUB4", targetQuantity = "100"),
            ),
            positions = mapOf("MXRF11" to "40", "ITUB4" to "120"),
            prices = mapOf("VALE3" to "100", "MXRF11" to "10", "ITUB4" to "50"),
            uninvestedBalance = "3600",
        )

        outcome.sells.map { it.ticker to it.source() } shouldBe listOf(
            "MXRF11" to FundingSource.OUTSIDE_STRATEGY,
            "ITUB4" to FundingSource.SURPLUS,
        )
        outcome.sells.map { it.quantity.plain() } shouldBe listOf("40", "20")
        outcome.summary.unfundedAmount.plain() shouldBe "0"
    }

    "counts an EXIT candidate's proceeds without selling those units again" {
        val outcome = compute(
            candidates = listOf(
                buy("VALE3", quantity = "50", price = "100"),
                exit("MXRF11", quantity = "40", price = "10"),
            ),
            slices = listOf(
                slice("VALE3", targetQuantity = "50"),
                slice("ITUB4", targetQuantity = "100"),
            ),
            positions = mapOf("MXRF11" to "40", "ITUB4" to "120"),
            prices = mapOf("VALE3" to "100", "MXRF11" to "10", "ITUB4" to "50"),
            uninvestedBalance = "3600",
        )

        outcome.sells.map { it.ticker to it.quantity.plain() } shouldBe listOf("ITUB4" to "20")
        outcome.summary.sellAmount.plain() shouldBe "1000"
        outcome.summary.unfundedAmount.plain() shouldBe "0"
    }

    "never sells a NEUTRO slice beyond the custody it still holds" {
        val outcome = compute(
            candidates = listOf(buy("VALE3", quantity = "100", price = "100")),
            slices = listOf(
                slice("VALE3", targetQuantity = "100"),
                slice("B3SA3", targetQuantity = "200", neutro = true, attributed = "80"),
            ),
            positions = mapOf("B3SA3" to "60"),
            prices = mapOf("VALE3" to "100", "B3SA3" to "25"),
            uninvestedBalance = "0",
        )

        outcome.sells.map { it.ticker to it.quantity.plain() } shouldBe listOf("B3SA3" to "60")
        outcome.summary.unfundedAmount.plain() shouldBe "8500"
    }

    "never sells a manually attributed position as an outside source" {
        val outcome = compute(
            candidates = listOf(buy("VALE3", quantity = "10", price = "100")),
            slices = listOf(slice("VALE3", targetQuantity = "10")),
            positions = mapOf("MXRF11" to "40"),
            prices = mapOf("VALE3" to "100", "MXRF11" to "10"),
            manualAttribution = mapOf("MXRF11" to "40"),
            uninvestedBalance = "0",
        )

        outcome.sells.shouldBeEmpty()
        outcome.summary.unfundedAmount.plain() shouldBe "1000"
    }

    "subtracts the manual units from a targeted surplus" {
        val outcome = compute(
            candidates = listOf(buy("VALE3", quantity = "55", price = "100")),
            slices = listOf(
                slice("VALE3", targetQuantity = "55"),
                slice("ITUB4", targetQuantity = "100"),
            ),
            positions = mapOf("ITUB4" to "120"),
            prices = mapOf("VALE3" to "100", "ITUB4" to "50"),
            manualAttribution = mapOf("ITUB4" to "10"),
            uninvestedBalance = "4500",
        )

        outcome.sells.map { it.ticker to it.quantity.plain() } shouldBe listOf("ITUB4" to "10")
        outcome.summary.unfundedAmount.plain() shouldBe "500"
    }

    "never sells an unclassified ticker" {
        val outcome = compute(
            candidates = listOf(buy("VALE3", quantity = "10", price = "100")),
            slices = listOf(slice("VALE3", targetQuantity = "10")),
            positions = mapOf("PETR4" to "40"),
            prices = mapOf("VALE3" to "100", "PETR4" to "10"),
            unclassifiedTickers = setOf("PETR4"),
            uninvestedBalance = "0",
        )

        outcome.sells.shouldBeEmpty()
        outcome.summary.unfundedAmount.plain() shouldBe "1000"
    }

    "leaves the remainder unfunded when every source is exhausted" {
        val outcome = compute(
            candidates = listOf(buy("VALE3", quantity = "50", price = "100")),
            slices = listOf(
                slice("VALE3", targetQuantity = "50"),
                slice("ITUB4", targetQuantity = "100"),
            ),
            positions = mapOf("ITUB4" to "110"),
            prices = mapOf("VALE3" to "100", "ITUB4" to "50"),
            uninvestedBalance = "4000",
        )

        outcome.sells.map { it.quantity.plain() } shouldBe listOf("10")
        outcome.summary.unfundedAmount.plain() shouldBe "500"
    }
})

private fun compute(
    candidates: List<OrderCandidate>,
    slices: List<TickerOrderPlan>,
    positions: Map<String, String> = emptyMap(),
    prices: Map<String, String> = emptyMap(),
    floor: String = "0",
    uninvestedBalance: String,
    manualAttribution: Map<String, String> = emptyMap(),
    unclassifiedTickers: Set<String> = emptySet(),
): FundingOutcome {
    val positionByTicker = positions.mapValues { BigDecimal(it.value) }
    return FundingCalculator.compute(
        FundingContext(
            candidates = candidates,
            slices = slices,
            positions = positionByTicker,
            prices = prices.mapValues { BigDecimal(it.value) },
            placement = PlacementCalculator.compute(
                positions = positionByTicker,
                targetedTickers = slices.filter { it.target.signum() > 0 }.map { it.ticker }.toSet(),
                ledger = manualAttribution.map { (ticker, quantity) ->
                    StrategyTicker("Legado", ticker) to BigDecimal(quantity)
                }.toMap(),
                manualStrategies = setOf("Legado"),
                unclassifiedTickers = unclassifiedTickers,
            ),
            floor = BigDecimal(floor),
            uninvestedBalance = BigDecimal(uninvestedBalance),
        ),
    )
}

private fun OrderCandidate.source(): FundingSource = (this as OrderCandidate.FundingSell).fundingSource

private fun buy(ticker: String, quantity: String, price: String) = OrderCandidate.traded(
    side = OrderSide.BUY,
    ticker = ticker,
    quantity = BigDecimal(quantity),
    price = BigDecimal(price),
    ideal = BigDecimal.ZERO,
    targetQuantity = BigDecimal.ZERO,
    position = BigDecimal.ZERO,
)

private fun exit(ticker: String, quantity: String, price: String) = OrderCandidate.traded(
    side = OrderSide.EXIT,
    ticker = ticker,
    quantity = BigDecimal(quantity),
    price = BigDecimal(price),
    ideal = BigDecimal.ZERO,
    targetQuantity = BigDecimal.ZERO,
    position = BigDecimal(quantity),
)

private fun slice(
    ticker: String,
    strategy: String = "Top",
    target: String = "100",
    targetQuantity: String,
    neutro: Boolean = false,
    attributed: String = "0",
) = TickerOrderPlan(
    ticker = ticker,
    strategy = strategy,
    target = BigDecimal(target),
    ideal = BigDecimal.ZERO,
    price = null,
    targetQuantity = BigDecimal(targetQuantity),
    neutro = neutro,
    attributedQuantity = BigDecimal(attributed),
)
