package dev.agner.portfolio.usecase.tickeralias

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when the rename edges loop instead of ending at a canonical code. A cycle has no latest
// ticker, so the resolver refuses rather than picking an arbitrary member of the loop.
class TickerAliasCycleException(ticker: String) : DomainException(
    error = "ticker-alias-cycle",
    userMessage = "The ticker alias chain contains a cycle",
    detail = "Ticker alias chain starting at $ticker never reaches a canonical ticker",
)
