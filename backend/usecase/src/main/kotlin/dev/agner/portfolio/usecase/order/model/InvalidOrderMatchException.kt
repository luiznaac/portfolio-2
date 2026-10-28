package dev.agner.portfolio.usecase.order.model

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when an explicit match override points an order at a trade that cannot be its execution:
// a different ticker, the opposite side, or an id that is not a trade. The override is a human
// correction, so a mismatch is a caller error (400), never a silently dropped link.
class InvalidOrderMatchException(reason: String) : DomainException(
    error = "invalid-order-match",
    userMessage = "The order match is not valid",
    detail = reason,
)
