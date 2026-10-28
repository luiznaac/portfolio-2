package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.averageApproximate
import dev.agner.portfolio.usecase.sell
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

private val gapDate = LocalDate.parse("2026-03-15")

class PositionCalculatorGapTest : StringSpec({

    "an upward GAP adds units at its placeholder price and makes the average approximate" {
        val position = PositionCalculator.replay(
            ticker = "PETR4",
            trades = listOf(buy(date = "2026-03-01", quantity = "100", unitPrice = "10")),
            gaps = listOf(gap(side = TradeSide.BUY, quantity = "50", unitPrice = "12")),
        )

        position.quantity.compareTo(BigDecimal("150")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("10.666667")) shouldBe 0
        gap(side = TradeSide.BUY, quantity = "50", unitPrice = "12").averageApproximate shouldBe true
    }

    "a downward GAP removes units without moving the average and keeps its realized result pending" {
        val position = PositionCalculator.replay(
            ticker = "PETR4",
            trades = listOf(buy(date = "2026-03-01", quantity = "100", unitPrice = "10")),
            gaps = listOf(gap(side = TradeSide.SELL, quantity = "40", unitPrice = null)),
        )

        position.quantity.compareTo(BigDecimal("60")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("10")) shouldBe 0
        (gap(side = TradeSide.SELL, quantity = "40", unitPrice = null) as Gap.Sell).realizedPending shouldBe true
    }

    "a replaced GAP no longer enters the replay" {
        val position = PositionCalculator.replay(
            ticker = "PETR4",
            trades = listOf(buy(date = "2026-03-01", quantity = "100", unitPrice = "10")),
            gaps = listOf(gap(side = TradeSide.BUY, quantity = "50", unitPrice = "12", status = GapStatus.REPLACED)),
        )

        position.quantity.compareTo(BigDecimal("100")) shouldBe 0
    }

    "the sale basis includes a same-day gap but excludes later ones" {
        val sale = sell(quantity = "10", date = "2026-03-20")
        val position = PositionCalculator.positionBefore(
            ticker = "PETR4",
            trades = listOf(buy(date = "2026-03-01", quantity = "100", unitPrice = "10"), sale),
            corporateActions = emptyList(),
            trade = sale,
            gaps = listOf(
                gap(side = TradeSide.BUY, quantity = "50", unitPrice = "12", date = LocalDate.parse("2026-03-15")),
                gap(side = TradeSide.BUY, quantity = "99", unitPrice = "12", date = LocalDate.parse("2026-03-25")),
            ),
        )

        position.quantity.compareTo(BigDecimal("150")) shouldBe 0
    }
})

internal fun gap(
    id: Int = 1,
    ticker: String = "PETR4",
    date: LocalDate = gapDate,
    side: TradeSide,
    quantity: String,
    unitPrice: String?,
    status: GapStatus = GapStatus.OPEN,
): Gap = when (side) {
    TradeSide.BUY -> Gap.Buy(
        id = id,
        ticker = ticker,
        date = date,
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal(unitPrice ?: "0"),
        status = status,
    )

    TradeSide.SELL -> Gap.Sell(
        id = id,
        ticker = ticker,
        date = date,
        quantity = BigDecimal(quantity),
        unitPrice = unitPrice?.let(::BigDecimal),
        status = status,
    )
}
