package dev.agner.portfolio.usecase.listedasset.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The natural tuple is the reconciliation key between a manual trade and an incoming B3 row, so
// its equality must be based on the numeric value, never on the BigDecimal scale the storage column
// happens to carry (trade quantity is decimal(18,6), so 100 reads back as 100.000000).
class TradeKeyTest : StringSpec({

    "keys with equal values compare equal regardless of BigDecimal scale" {
        key(quantity = BigDecimal("100"), unitPrice = BigDecimal("48.92")) shouldBe
            key(quantity = BigDecimal("100.000000"), unitPrice = BigDecimal("48.920000"))
    }

    "keys differing in any tuple component are not equal" {
        key() shouldNotBe key(side = TradeSide.SELL)
        key() shouldNotBe key(venue = "FRACIONARIO")
        key() shouldNotBe key(ticker = "VALE3")
        key() shouldNotBe key(quantity = BigDecimal("200"))
        key() shouldNotBe key(unitPrice = BigDecimal("49.00"))
        key() shouldNotBe key(institutionId = 9)
        key() shouldNotBe key(date = LocalDate.parse("2026-10-15"))
    }

    "a creation's natural key normalizes the ticker" {
        TradeCreation(
            ticker = "petr4f",
            side = TradeSide.BUY,
            date = LocalDate.parse("2026-10-14"),
            venue = "VISTA",
            quantity = BigDecimal("100"),
            unitPrice = BigDecimal("48.92"),
            institutionId = 3,
        ).naturalKey() shouldBe key(ticker = "PETR4")
    }
})

private fun key(
    date: LocalDate = LocalDate.parse("2026-10-14"),
    side: TradeSide = TradeSide.BUY,
    venue: String = "VISTA",
    ticker: String = "PETR4",
    quantity: BigDecimal = BigDecimal("100"),
    unitPrice: BigDecimal = BigDecimal("48.92"),
    institutionId: Int = 3,
) = TradeKey(
    date = date,
    side = side,
    venue = venue,
    ticker = ticker,
    quantity = quantity,
    unitPrice = unitPrice,
    institutionId = institutionId,
)
