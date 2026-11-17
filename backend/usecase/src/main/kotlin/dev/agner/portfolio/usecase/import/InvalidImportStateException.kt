package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.import.model.ImportStatus

// Raised when a batch action is asked of an import in the wrong lifecycle state — reviewing
// something already applied, applying something not yet reviewed. `expected` is a set because an
// edit such as a ticker confirmation is legal while the record is either staged or reviewed.
class InvalidImportStateException(
    id: Int,
    expected: Set<ImportStatus>,
    actual: ImportStatus,
) : DomainException(
    error = "invalid-import-state",
    userMessage = "Import is not in a state that allows this action",
    detail = "Import $id must be one of ${expected.joinToString()} but is $actual",
) {
    constructor(id: Int, expected: ImportStatus, actual: ImportStatus) :
        this(id, setOf(expected), actual)
}
