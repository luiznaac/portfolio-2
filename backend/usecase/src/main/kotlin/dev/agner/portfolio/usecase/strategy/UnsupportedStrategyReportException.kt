package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when an uploaded report matches no XP layout. The upload is rejected rather than staged
// empty: a file the pipeline cannot read is a caller error, not an edition with no tickers.
class UnsupportedStrategyReportException(fileName: String) : DomainException(
    error = "unsupported-strategy-report",
    userMessage = "The uploaded report layout is not recognised",
    detail = "No XP report parser recognises the file '$fileName'",
)
