package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.import.model.ImportDestination

// The typed destination is recorded for every pipeline, but only the ones whose assistant variant
// has landed can stage and apply. Refusing early keeps an INCOME/CORPORATE_ACTIONS record from
// silently writing trades.
class UnsupportedImportDestinationException(
    destination: ImportDestination,
) : DomainException(
    error = "unsupported-import-destination",
    userMessage = "Import destination is not supported yet",
    detail = "No import pipeline applies destination ${destination.objectType} yet",
)
