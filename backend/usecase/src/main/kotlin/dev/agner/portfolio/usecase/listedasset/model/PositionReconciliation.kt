package dev.agner.portfolio.usecase.listedasset.model

import java.math.BigDecimal

// One ticker's custody-vs-ledger comparison. `hasDrift` is the attention signal: the browser
// custody export and the replayed ledger disagree. It is read-only — a drift never edits the
// ledger, it only asks the owner to look.
data class PositionReconciliation(
    val ticker: String,
    val custodyQuantity: BigDecimal,
    val ledgerQuantity: BigDecimal,
) {
    val hasDrift: Boolean
        get() = custodyQuantity.compareTo(ledgerQuantity) != 0
}
