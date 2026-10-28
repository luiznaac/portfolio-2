package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.commons.DomainException

// A report whose layout was claimed but whose contents failed a hard check (missing reference
// month, no portfolio table rows, weights not summing to 100). Failing loudly keeps a bad parse
// from silently becoming an edition; the reviewer can still import a corrected file.
class InvalidStrategyReportException(fileName: String, reason: String) : DomainException(
    error = "invalid-strategy-report",
    userMessage = "The uploaded report could not be parsed",
    detail = "Report '$fileName' failed validation: $reason",
)
