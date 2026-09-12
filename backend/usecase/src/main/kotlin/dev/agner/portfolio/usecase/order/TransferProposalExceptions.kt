package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus
import java.math.BigDecimal

class TransferProposalNotFoundException(id: Int) :
    DomainException(
        error = "transfer-proposal-not-found",
        userMessage = "Transfer proposal not found",
        detail = "Transfer proposal $id not found for the current month",
    )

class TransferProposalNotPendingException(id: Int, status: TransferProposalStatus) :
    DomainException(
        error = "transfer-proposal-not-pending",
        userMessage = "Transfer proposal is not pending",
        detail = "Transfer proposal $id is $status, not PENDENTE",
    )

class InvalidTransferQuantityException(approved: BigDecimal, proposed: BigDecimal) :
    DomainException(
        error = "invalid-transfer-quantity",
        userMessage = "Invalid transfer quantity",
        detail = "Approved quantity $approved must be between 0 and $proposed",
    )
