package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when a resolve names an event the log does not hold.
class CorporateActionNotFoundException(id: Int) : DomainException(
    error = "corporate-action-not-found",
    userMessage = "Corporate action not found",
    detail = "Corporate action $id does not exist",
)
