package dev.agner.portfolio.usecase.tax.model

import kotlinx.datetime.YearMonth

// The open month's stock-sale tax read: the exemption meter, the fiscal average price of every held
// ticker, the realized result per tax kind, and the carry state that result rolls. Computed on read
// from the ledger, so it can never drift from the trades and corporate actions it is derived from.
data class StockTaxReport(
    val period: YearMonth,
    val meter: ExemptionMeter,
    val averagePrices: List<TickerAveragePrice>,
    val realized: List<RealizedResult>,
    val carry: List<CarryBucket>,
    val dayTrades: List<DayTradeWarning>,
)
