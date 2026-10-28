package dev.agner.portfolio.usecase.order.repository

import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalCreation
import dev.agner.portfolio.usecase.order.model.TransferStatus

interface ITransferProposalRepository {

    suspend fun fetchBy(monthId: Int): List<TransferProposal>

    suspend fun fetchById(id: Int): TransferProposal?

    // A refresh replaces the undecided inbox; decided proposals stay as the month's trail.
    suspend fun deletePending(monthId: Int)

    suspend fun save(monthId: Int, creation: TransferProposalCreation): TransferProposal

    suspend fun decide(id: Int, status: TransferStatus): TransferProposal?
}
