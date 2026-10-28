package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.import.model.ImportStatus

// Raised when a batch action is asked of an import in the wrong lifecycle state — reviewing
// something already applied, applying something not yet reviewed.
class InvalidImportStateException(
    id: Int,
    expected: ImportStatus,
    actual: ImportStatus,
) : DomainException(
    error = "invalid-import-state",
    userMessage = "Import is not in a state that allows this action",
    detail = "Import $id must be $expected but is $actual",
)
