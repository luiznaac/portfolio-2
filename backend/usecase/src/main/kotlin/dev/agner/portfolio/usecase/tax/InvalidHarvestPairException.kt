package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when an accepted pair cannot be a harvest: no held position, an opted-out ticker, a
// same-day rebuy, a non-positive leg, or a sell price at or above the fiscal average price (which
// would realize no loss). The pair is a human decision, so a bad one is a caller error (400).
class InvalidHarvestPairException(reason: String) : DomainException(
    error = "invalid-harvest-pair",
    userMessage = "The harvest pair is not valid",
    detail = reason,
)
