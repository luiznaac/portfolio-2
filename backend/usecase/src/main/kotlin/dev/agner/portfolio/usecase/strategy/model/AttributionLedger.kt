package dev.agner.portfolio.usecase.strategy.model

import java.math.BigDecimal

// The attribution ledger keyed by (strategy, ticker) — the shape the allocation tree and the order
// engine both consume.
typealias AttributionLedger = Map<StrategyTicker, BigDecimal>
