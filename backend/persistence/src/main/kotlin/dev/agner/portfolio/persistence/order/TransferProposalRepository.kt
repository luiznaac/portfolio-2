package dev.agner.portfolio.persistence.order

import dev.agner.portfolio.persistence.listedasset.ListedAssetEntity
import dev.agner.portfolio.persistence.strategy.StrategyEntity
import dev.agner.portfolio.persistence.strategy.isReferenceDateConflict
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.order.TransferProposalNotPendingException
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalCreation
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock

@Component
class TransferProposalRepository(
    private val clock: Clock,
) : ITransferProposalRepository {

    override suspend fun fetchByMonth(month: LocalDate) = transaction {
        TransferProposalEntity.find { TransferProposalTable.month eq month }.map { it.toModel() }
    }

    override suspend fun fetchById(id: Int) = transaction {
        TransferProposalEntity.findById(id)?.toModel()
    }

    override suspend fun find(
        month: LocalDate,
        listedAssetId: Int,
        fromStrategyId: Int,
        toStrategyId: Int,
    ) = transaction {
        TransferProposalEntity.find {
            (TransferProposalTable.month eq month) and
                (TransferProposalTable.listedAsset eq listedAssetId) and
                (TransferProposalTable.fromStrategy eq fromStrategyId) and
                (TransferProposalTable.toStrategy eq toStrategyId)
        }.firstOrNull()?.toModel()
    }

    override suspend fun save(creation: TransferProposalCreation): TransferProposal = try {
        transaction {
            TransferProposalEntity.new {
                month = creation.month
                listedAsset = ListedAssetEntity.findById(creation.listedAssetId)
                    ?: throw IllegalArgumentException("ListedAsset ${creation.listedAssetId} not found")
                ticker = creation.ticker
                fromStrategy = StrategyEntity.findById(creation.fromStrategyId)
                    ?: throw IllegalArgumentException("Strategy ${creation.fromStrategyId} not found")
                toStrategy = StrategyEntity.findById(creation.toStrategyId)
                    ?: throw IllegalArgumentException("Strategy ${creation.toStrategyId} not found")
                proposedQuantity = creation.proposedQuantity
                appliedQuantity = null
                status = TransferProposalStatus.PENDING
                decidedAt = null
                createdAt = LocalDateTime.now(clock)
            }.toModel()
        }
    } catch (e: ExposedSQLException) {
        // uniqueIndex(month, asset, from, to) closes the service's check-then-insert race: if a
        // concurrent request inserted the pairing first, return that row instead of leaking the
        // duplicate-key error. Any other database failure still propagates.
        if (isReferenceDateConflict(e.errorCode, e.sqlState)) {
            find(creation.month, creation.listedAssetId, creation.fromStrategyId, creation.toStrategyId)
                ?: throw e
        } else {
            throw e
        }
    }

    override suspend fun updateProposedQuantity(id: Int, quantity: BigDecimal) = transaction {
        val entity = findEntity(id)
        entity.proposedQuantity = quantity
        entity.toModel()
    }

    override suspend fun decide(
        id: Int,
        status: TransferProposalStatus,
        appliedQuantity: BigDecimal?,
        decidedAt: LocalDateTime,
    ) = transaction {
        val updated = TransferProposalTable.update({
            (TransferProposalTable.id eq id) and (TransferProposalTable.status eq TransferProposalStatus.PENDING)
        }) {
            it[TransferProposalTable.status] = status
            it[TransferProposalTable.appliedQuantity] = appliedQuantity
            it[TransferProposalTable.decidedAt] = decidedAt
        }

        if (updated == 0) {
            // The status column is the guard, so a second decide — a double click, a retry, or a
            // concurrent request that read PENDING before the first commit — can never apply the
            // proposal twice. findEntity keeps the unknown-id contract and reports the current
            // status.
            throw TransferProposalNotPendingException(id, findEntity(id).status)
        }

        findEntity(id).toModel()
    }

    private fun findEntity(id: Int) =
        TransferProposalEntity.findById(id) ?: throw IllegalArgumentException("TransferProposal $id not found")
}
