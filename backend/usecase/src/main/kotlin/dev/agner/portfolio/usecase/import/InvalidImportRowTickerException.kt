package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when the owner's answer for a broker description normalizes to no ticker at all — an empty
// answer would write an alias that resolves every later note to nothing.
class InvalidImportRowTickerException(ticker: String) : DomainException(
    error = "invalid-import-row-ticker",
    userMessage = "Invalid ticker",
    detail = "Ticker '$ticker' is not a valid ticker",
)
