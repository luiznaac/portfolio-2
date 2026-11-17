package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when a confirmation is asked of a row a broker note never printed a description for — a
// ticker layout's row already carries the ticker the file named, so there is nothing to confirm.
class ImportRowNotConfirmableException(rowId: Int) : DomainException(
    error = "import-row-not-confirmable",
    userMessage = "Import row cannot take a ticker",
    detail = "Import row $rowId has no broker description to confirm",
)
