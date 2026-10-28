package dev.agner.portfolio.usecase.monthlyclose

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.order.model.TransferProposal

// Raised when the close cannot proceed because the transfer inbox still holds undecided proposals.
// The refreshed inbox is carried so the edge can return the list with its 409 — the caller must
// decide (or reject) every transfer, never have the close silently discard them.
class PendingTransfersException(
    val pending: List<TransferProposal>,
) : DomainException(
    error = "pending-transfers",
    userMessage = "The month has undecided transfers",
    detail = pending.joinToString(
        separator = ", ",
        prefix = "Close blocked by ${pending.size} pending transfer(s): ",
    ) { "${it.ticker} ${it.fromStrategy} -> ${it.toStrategy}" },
)
