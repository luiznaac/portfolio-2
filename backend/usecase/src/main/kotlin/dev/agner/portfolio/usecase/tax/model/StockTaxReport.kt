package dev.agner.portfolio.usecase.tax.model

import kotlinx.datetime.YearMonth

// The open month's stock-sale tax read: the exemption meter plus the fiscal average price of every
// held ticker. Computed on read from the ledger, so it can never drift from the trades and
// corporate actions it is derived from.
data class StockTaxReport(
    val period: YearMonth,
    val meter: ExemptionMeter,
    val averagePrices: List<TickerAveragePrice>,
)
