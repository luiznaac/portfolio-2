package dev.agner.portfolio.usecase.strategy.model

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import java.math.BigDecimal

// A line inside an Edition (CONTEXT.md -> Edition Entry): ticker, weight, rating and target price.
// `doNotTrade` is the "não operar" flag — per-Edition only (D7) and never set by a parser, only by a
// reviewer's override.
data class EditionEntry(
    val ticker: String,
    val weight: BigDecimal,
    val rating: Rating? = null,
    val targetPrice: BigDecimal? = null,
    val doNotTrade: Boolean = false,
)

// Every ticker crossing the domain boundary is canonicalised, exactly as a Trade or a target chain
// is (see normalizedTicker): a report's fractional-market `F` suffix is stripped once, here.
fun List<EditionEntry>.normalizedEntries(): List<EditionEntry> =
    map { it.copy(ticker = it.ticker.normalizedTicker()) }
