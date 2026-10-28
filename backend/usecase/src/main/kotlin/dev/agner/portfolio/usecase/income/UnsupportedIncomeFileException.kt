package dev.agner.portfolio.usecase.income

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when an uploaded file is not a recognised B3 "Proventos a Receber" export. The import is
// refused rather than recorded as an empty set of movements.
class UnsupportedIncomeFileException(fileName: String) : DomainException(
    error = "unsupported-income-file",
    userMessage = "The uploaded file layout is not recognised",
    detail = "No income parser recognises the file '$fileName'",
)
