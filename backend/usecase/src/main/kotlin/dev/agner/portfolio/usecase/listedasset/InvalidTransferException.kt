package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when an Attribution Transfer cannot be applied: the source slice does not hold enough
// units, the source and destination are the same Strategy, or either Strategy's class differs from
// the ticker's. The same domain operation serves the manual endpoint and the order lifecycle, so
// this is a caller error (400), never a silent partial move.
class InvalidTransferException(reason: String) : DomainException(
    error = "invalid-transfer",
    userMessage = "The transfer is not valid",
    detail = reason,
)
