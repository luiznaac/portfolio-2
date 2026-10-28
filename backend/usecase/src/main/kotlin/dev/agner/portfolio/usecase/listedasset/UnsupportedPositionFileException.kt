package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when an uploaded file is not a recognised B3 "Posição" export. The cross-check is refused
// rather than run against an empty custody set, which would report every ledger ticker as drift.
class UnsupportedPositionFileException(fileName: String) : DomainException(
    error = "unsupported-position-file",
    userMessage = "The uploaded file layout is not recognised",
    detail = "No position parser recognises the file '$fileName'",
)
