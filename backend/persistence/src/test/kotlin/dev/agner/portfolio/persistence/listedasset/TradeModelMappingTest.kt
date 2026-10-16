package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The `trade` table keeps the direction in a single signed `quantity` column; this is the one place
// that translates it back into the Buy/Sell algebraic type (backend/AGENTS.md).
class TradeModelMappingTest : StringSpec({

    "a positive signed quantity maps to a buy keeping the quantity positive" {
        tradeModel(quantity = BigDecimal("100")) shouldBe Trade.Buy(
            id = 1,
            ticker = "PETR4",
            date = LocalDate.parse("2026-10-14"),
            venue = "VISTA",
            quantity = BigDecimal("100"),
            unitPrice = BigDecimal("48.92"),
            institutionId = 3,
            fee = null,
            source = TradeSource.MANUAL,
            confirmedByB3 = false,
            ordinal = 1,
        )
    }

    "a negative signed quantity maps to a sell with the quantity made positive" {
        tradeModel(quantity = BigDecimal("-100")) shouldBe Trade.Sell(
            id = 1,
            ticker = "PETR4",
            date = LocalDate.parse("2026-10-14"),
            venue = "VISTA",
            quantity = BigDecimal("100"),
            unitPrice = BigDecimal("48.92"),
            institutionId = 3,
            fee = null,
            source = TradeSource.MANUAL,
            confirmedByB3 = false,
            ordinal = 1,
        )
    }

    "a zero quantity is rejected rather than guessed" {
        shouldThrow<IllegalStateException> { tradeModel(quantity = BigDecimal.ZERO) }
    }
})

private fun tradeModel(quantity: BigDecimal) = tradeModel(
    id = 1,
    ticker = "PETR4",
    date = LocalDate.parse("2026-10-14"),
    venue = "VISTA",
    quantity = quantity,
    unitPrice = BigDecimal("48.92"),
    institutionId = 3,
    fee = null,
    source = TradeSource.MANUAL,
    confirmedByB3 = false,
    ordinal = 1,
)
