package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.commons.DomainException
import kotlinx.datetime.YearMonth

// An edition whose reviewed entries are not a valid portfolio (no rows, a repeated ticker, or a sum
// away from 100). The browser checks the same rule before it submits, but the server is the one that
// persists, so the rule has a home here too — a bad payload is a caller error.
class InvalidEditionException(
    strategyId: Int,
    referenceMonth: YearMonth,
    reason: String,
) : DomainException(
    error = "invalid-edition",
    userMessage = "The edition weights are not valid",
    detail = "Strategy $strategyId edition for $referenceMonth failed validation: $reason",
)
