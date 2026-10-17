package dev.agner.portfolio.usecase.listedasset.model

import java.math.BigDecimal

// One custody line from a B3 "Posição" export: a ticker and the units held, per institution. The
// reconciliation sums these by ticker before comparing them against the ledger projection, so the
// per-institution split never reaches the comparison.
data class CustodyPosition(
    val ticker: String,
    val quantity: BigDecimal,
)
