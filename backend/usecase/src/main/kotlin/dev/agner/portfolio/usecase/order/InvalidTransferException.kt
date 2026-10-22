package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when a transfer can no longer be applied: the proposal points at a slice the ledger no
// longer carries, or the source slice holds fewer units than the proposal would move. A pending
// proposal can go stale between a refresh and a decision, so this is a caller error (409/400), never
// a silent partial move.
class InvalidTransferException(reason: String) : DomainException(
    error = "invalid-transfer",
    userMessage = "The transfer is not valid",
    detail = reason,
)
