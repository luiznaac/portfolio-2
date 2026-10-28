package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import dev.agner.portfolio.usecase.plain
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The lifecycle's matching rule, pure: a suggested order is matched by ticker + side against the
// month's executed trades, and cumulative quantity decides whether it stays suggested or reaches
// executed/imported. Unplanned trades are the month's trades no suggestion asked for.
class OrderMatchingCalculatorTest : StringSpec({

    "keeps a suggestion with no matching trade SUGGESTED" {
        val matching = OrderMatchingCalculator.match(
            candidates = listOf(buy("ITUB4", "70")),
            trades = emptyList(),
        )

        val order = matching.orders.single()
        order.ticker shouldBe "ITUB4"
        order.status shouldBe SuggestedOrderStatus.SUGGESTED
        order.executedQuantity.plain() shouldBe "0"
        order.matchedTradeId shouldBe null
        matching.unplannedTrades.shouldBeEmpty()
    }

    "cumulates the month's trades by ticker and side until the suggestion is executed" {
        val matching = OrderMatchingCalculator.match(
            candidates = listOf(buy("ITUB4", "70")),
            trades = listOf(
                trade(1, "ITUB4", TradeSide.BUY, "40"),
                trade(2, "ITUB4", TradeSide.BUY, "30"),
            ),
        )

        val order = matching.orders.single()
        order.status shouldBe SuggestedOrderStatus.EXECUTED
        order.executedQuantity.plain() shouldBe "70"
        // The first execution is the match's evidence; cumulative quantity is what decides status.
        order.matchedTradeId shouldBe 1
    }

    "stays SUGGESTED while the cumulative quantity is short of the suggestion" {
        val matching = OrderMatchingCalculator.match(
            candidates = listOf(buy("ITUB4", "70")),
            trades = listOf(trade(1, "ITUB4", TradeSide.BUY, "40")),
        )

        val order = matching.orders.single()
        order.status shouldBe SuggestedOrderStatus.SUGGESTED
        order.executedQuantity.plain() shouldBe "40"
        order.matchedTradeId shouldBe 1
    }

    "marks the order IMPORTED when the matched trades came from a B3 import" {
        val matching = OrderMatchingCalculator.match(
            candidates = listOf(buy("ITUB4", "70")),
            trades = listOf(
                trade(1, "ITUB4", TradeSide.BUY, "40", TradeSource.B3_IMPORT),
                trade(2, "ITUB4", TradeSide.BUY, "30"),
            ),
        )

        matching.orders.single().status shouldBe SuggestedOrderStatus.IMPORTED
    }

    "marks a manual trade confirmed by B3 as IMPORTED" {
        val matching = OrderMatchingCalculator.match(
            candidates = listOf(buy("ITUB4", "70")),
            trades = listOf(
                trade(1, "ITUB4", TradeSide.BUY, "70", TradeSource.MANUAL, confirmedByB3 = true),
            ),
        )

        matching.orders.single().status shouldBe SuggestedOrderStatus.IMPORTED
    }

    "never matches a BUY suggestion with a SELL trade" {
        val matching = OrderMatchingCalculator.match(
            candidates = listOf(buy("ITUB4", "70")),
            trades = listOf(trade(1, "ITUB4", TradeSide.SELL, "70")),
        )

        val order = matching.orders.single()
        order.status shouldBe SuggestedOrderStatus.SUGGESTED
        order.executedQuantity.plain() shouldBe "0"
        matching.unplannedTrades.map { it.id } shouldBe listOf(1)
    }

    "maps NEW_ENTRY to a BUY trade and EXIT to a SELL trade" {
        val matching = OrderMatchingCalculator.match(
            candidates = listOf(
                newEntry("B3SA3", "200"),
                exit("MXRF11", "300"),
            ),
            trades = listOf(
                trade(1, "B3SA3", TradeSide.BUY, "200"),
                trade(2, "MXRF11", TradeSide.SELL, "300"),
            ),
        )

        matching.orders.associate { it.ticker to it.status } shouldBe mapOf(
            "B3SA3" to SuggestedOrderStatus.EXECUTED,
            "MXRF11" to SuggestedOrderStatus.EXECUTED,
        )
        matching.unplannedTrades.shouldBeEmpty()
    }

    "reports every trade no suggestion asked for as unplanned" {
        val matching = OrderMatchingCalculator.match(
            candidates = listOf(buy("ITUB4", "70")),
            trades = listOf(
                trade(1, "ITUB4", TradeSide.BUY, "70"),
                trade(2, "CURY3", TradeSide.SELL, "40"),
            ),
        )

        matching.orders.map { it.side } shouldBe listOf(OrderSide.BUY)
        matching.unplannedTrades.map { it.ticker } shouldBe listOf("CURY3")
    }
})

private fun buy(ticker: String, quantity: String) = candidate(OrderSide.BUY, ticker, quantity)

private fun newEntry(ticker: String, quantity: String) = candidate(OrderSide.NEW_ENTRY, ticker, quantity)

private fun exit(ticker: String, quantity: String) = candidate(OrderSide.EXIT, ticker, quantity)

private fun candidate(side: OrderSide, ticker: String, quantity: String): OrderCandidate = when (side) {
    OrderSide.BUY -> OrderCandidate.traded(
        side = side,
        ticker = ticker,
        quantity = BigDecimal(quantity),
        price = BigDecimal("10"),
        ideal = BigDecimal("100"),
        targetQuantity = BigDecimal(quantity),
        position = BigDecimal.ZERO,
    )
    OrderSide.NEW_ENTRY -> OrderCandidate.traded(
        side = side,
        ticker = ticker,
        quantity = BigDecimal(quantity),
        price = BigDecimal("10"),
        ideal = BigDecimal("100"),
        targetQuantity = BigDecimal(quantity),
        position = BigDecimal.ZERO,
    )
    OrderSide.EXIT -> OrderCandidate.traded(
        side = side,
        ticker = ticker,
        quantity = BigDecimal(quantity),
        price = BigDecimal("10"),
        ideal = BigDecimal.ZERO,
        targetQuantity = BigDecimal.ZERO,
        position = BigDecimal(quantity),
    )
    OrderSide.SELL -> OrderCandidate.fundingSell(
        ticker = ticker,
        quantity = BigDecimal(quantity),
        price = BigDecimal("10"),
        ideal = BigDecimal.ZERO,
        targetQuantity = BigDecimal.ZERO,
        position = BigDecimal(quantity),
        fundingSource = dev.agner.portfolio.usecase.order.model.FundingSource.SURPLUS,
    )
}

private fun trade(
    id: Int,
    ticker: String,
    side: TradeSide,
    quantity: String,
    source: TradeSource = TradeSource.MANUAL,
    confirmedByB3: Boolean = false,
): Trade = when (side) {
    TradeSide.BUY -> Trade.Buy(
        id = id,
        ticker = ticker,
        date = LocalDate.parse("2026-10-05"),
        venue = "B3",
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal("10"),
        institutionId = 1,
        fee = null,
        source = source,
        confirmedByB3 = confirmedByB3,
        ordinal = 1,
    )
    TradeSide.SELL -> Trade.Sell(
        id = id,
        ticker = ticker,
        date = LocalDate.parse("2026-10-05"),
        venue = "B3",
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal("10"),
        institutionId = 1,
        fee = null,
        source = source,
        confirmedByB3 = confirmedByB3,
        ordinal = 1,
    )
}
