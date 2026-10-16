package dev.agner.portfolio.usecase.listedasset.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The "always-positive quantity" rule is a type invariant, not a parser convention: a non-positive
// quantity cannot be constructed at all.
class TradeTest : StringSpec({

    "a buy with a non-positive quantity is rejected" {
        shouldThrow<IllegalArgumentException> { trade(quantity = BigDecimal("0")) }
        shouldThrow<IllegalArgumentException> { trade(quantity = BigDecimal("-1")) }
    }

    "a sell with a non-positive quantity is rejected" {
        shouldThrow<IllegalArgumentException> { sell(quantity = BigDecimal("0")) }
        shouldThrow<IllegalArgumentException> { sell(quantity = BigDecimal("-1")) }
    }

    "a creation with a non-positive quantity is rejected" {
        shouldThrow<IllegalArgumentException> {
            TradeCreation(
                ticker = "PETR4",
                side = TradeSide.BUY,
                date = LocalDate.parse("2026-10-14"),
                venue = "VISTA",
                quantity = BigDecimal("-1"),
                unitPrice = BigDecimal("48.92"),
                institutionId = 1,
            )
        }
    }
})

private fun trade(quantity: BigDecimal) = Trade.Buy(
    id = 1,
    ticker = "PETR4",
    date = LocalDate.parse("2026-10-14"),
    venue = "VISTA",
    quantity = quantity,
    unitPrice = BigDecimal("48.92"),
    institutionId = 1,
    fee = null,
    source = TradeSource.MANUAL,
    confirmedByB3 = false,
    ordinal = 1,
)

private fun sell(quantity: BigDecimal) = Trade.Sell(
    id = 1,
    ticker = "PETR4",
    date = LocalDate.parse("2026-10-14"),
    venue = "VISTA",
    quantity = quantity,
    unitPrice = BigDecimal("48.92"),
    institutionId = 1,
    fee = null,
    source = TradeSource.MANUAL,
    confirmedByB3 = false,
    ordinal = 1,
)
