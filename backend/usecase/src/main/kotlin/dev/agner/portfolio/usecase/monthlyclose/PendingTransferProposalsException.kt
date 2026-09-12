package dev.agner.portfolio.usecase.monthlyclose

import dev.agner.portfolio.usecase.commons.DomainException

class PendingTransferProposalsException(count: Int) :
    DomainException(
        error = "pending-transfer-proposals",
        userMessage = "Pending transfer proposals block the close",
        detail = "Cannot close the month with $count pending transfer proposal(s)",
    )
