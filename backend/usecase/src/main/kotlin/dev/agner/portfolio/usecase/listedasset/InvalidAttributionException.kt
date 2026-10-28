package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when a slice would break the attribution invariant — a non-positive quantity, or a total
// above the position's custody. The ticket's rule is Σ attributions ≤ Position.quantity; the server
// enforces it here so a stale browser cannot persist more than the position holds.
class InvalidAttributionException(reason: String) : DomainException(
    error = "invalid-attribution",
    userMessage = "The attribution is not valid",
    detail = reason,
)
