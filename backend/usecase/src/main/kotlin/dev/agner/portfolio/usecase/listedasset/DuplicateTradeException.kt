package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.listedasset.model.TradeKey

// Raised when a manual trade is entered that duplicates a trade already imported from a broker
// file: the B3 row is the source of truth, so the manual copy is refused rather than silently
// sitting beside it. The inverse of the "manual trade confirmed by B3" rule, where the manual trade
// wins — there the B3 row arrives second and is skipped.
class DuplicateTradeException(key: TradeKey) : DomainException(
    error = "duplicate-trade",
    userMessage = "A trade with the same details was already imported",
    detail = "Manual trade duplicates ${key.ticker} ${key.side} ${key.date} (${key.quantity} @ ${key.unitPrice})",
)
