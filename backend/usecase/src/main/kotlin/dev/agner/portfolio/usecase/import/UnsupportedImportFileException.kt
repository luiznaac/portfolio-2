package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when no registered parser recognises an uploaded file's layout. The upload is rejected
// outright rather than staged with zero rows: a file the pipeline cannot read is a caller error,
// not an empty import.
class UnsupportedImportFileException(fileName: String) : DomainException(
    error = "unsupported-import-file",
    userMessage = "The uploaded file layout is not recognised",
    detail = "No import parser recognises the file '$fileName'",
)
