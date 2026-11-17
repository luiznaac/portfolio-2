package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.commons.DomainException

// A sync that cannot run at all: no brapi token configured, or the provider rejected the token
// (401/403). It is loud on purpose — a paid one-off sync must never look like a run that simply
// found nothing.
class BrapiSyncException(
    detail: String,
    userMessage: String = "The brapi sync cannot run",
) : DomainException(
    error = "brapi-sync-unavailable",
    userMessage = userMessage,
    detail = detail,
)
