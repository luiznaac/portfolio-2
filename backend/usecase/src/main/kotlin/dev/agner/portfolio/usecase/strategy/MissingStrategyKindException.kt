package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.commons.DomainException

// A Strategy is either report-driven or manual; there is no default kind, so a creation without
// one is a caller error rather than a guess.
class MissingStrategyKindException : DomainException(
    error = "missing-strategy-kind",
    userMessage = "A strategy kind is required",
    detail = "A strategy must be created as REPORT_DRIVEN or MANUAL",
)
