package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// Shared builders for the listed-asset tests: one trade shape, so a spec states only the fields it
// cares about instead of re-deriving the Buy/Sell constructors in every file.

fun buy(
    id: Int = 1,
    ticker: String = "PETR4",
    date: String = "2026-10-01",
    quantity: String = "100",
    unitPrice: String = "10",
    institutionId: Int = 1,
    fee: String? = null,
    source: TradeSource = TradeSource.B3_IMPORT,
    confirmedByB3: Boolean = false,
    ordinal: Int = id,
    venue: String = "VISTA",
) = Trade.Buy(
    id = id,
    ticker = ticker,
    date = LocalDate.parse(date),
    venue = venue,
    quantity = BigDecimal(quantity),
    unitPrice = BigDecimal(unitPrice),
    institutionId = institutionId,
    fee = fee?.let(::BigDecimal),
    source = source,
    confirmedByB3 = confirmedByB3,
    ordinal = ordinal,
)

fun sell(
    id: Int = 1,
    ticker: String = "PETR4",
    date: String = "2026-10-05",
    quantity: String = "100",
    unitPrice: String = "10",
    institutionId: Int = 1,
    fee: String? = null,
    source: TradeSource = TradeSource.B3_IMPORT,
    confirmedByB3: Boolean = false,
    ordinal: Int = id,
    venue: String = "VISTA",
) = Trade.Sell(
    id = id,
    ticker = ticker,
    date = LocalDate.parse(date),
    venue = venue,
    quantity = BigDecimal(quantity),
    unitPrice = BigDecimal(unitPrice),
    institutionId = institutionId,
    fee = fee?.let(::BigDecimal),
    source = source,
    confirmedByB3 = confirmedByB3,
    ordinal = ordinal,
)
