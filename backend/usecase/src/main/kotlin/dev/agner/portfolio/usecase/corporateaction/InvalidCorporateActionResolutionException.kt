package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when a decision cannot resolve a parked event: the row is not waiting for review, or the
// supplied data still leaves the engine without what it needs to apply the event.
class InvalidCorporateActionResolutionException(id: Int, reason: String) : DomainException(
    error = "invalid-corporate-action-resolution",
    userMessage = "The corporate action cannot be resolved with this decision",
    detail = "Corporate action $id: $reason",
)
