package dev.agner.portfolio.usecase.order.repository

import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalCreation
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

interface ITransferProposalRepository {

    suspend fun fetchByMonth(month: LocalDate): List<TransferProposal>

    /** Any status — used to decide whether a freshly matched pairing already has a row this month. */
    suspend fun find(month: LocalDate, listedAssetId: Int, fromStrategyId: Int, toStrategyId: Int): TransferProposal?

    suspend fun save(creation: TransferProposalCreation): TransferProposal

    /** Refreshes a still-PENDENTE proposal's quantity as the underlying deltas move. */
    suspend fun updateProposedQuantity(id: Int, quantity: BigDecimal): TransferProposal

    suspend fun decide(
        id: Int,
        status: TransferProposalStatus,
        appliedQuantity: BigDecimal?,
        decidedAt: LocalDateTime,
    ): TransferProposal
}
