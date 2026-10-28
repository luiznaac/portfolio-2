package dev.agner.portfolio.persistence.order

import dev.agner.portfolio.persistence.portfolio.MonthEntity
import dev.agner.portfolio.persistence.strategy.StrategyEntity
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalCreation
import dev.agner.portfolio.usecase.order.model.TransferStatus
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class TransferProposalRepository(
    private val clock: Clock,
) : ITransferProposalRepository {

    override suspend fun fetchBy(monthId: Int): List<TransferProposal> = transaction {
        TransferProposalEntity.find { TransferProposalTable.monthId eq monthId }
            .orderBy(TransferProposalTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchById(id: Int): TransferProposal? = transaction {
        TransferProposalEntity.findById(id)?.toModel()
    }

    override suspend fun deletePending(monthId: Int) {
        transaction {
            TransferProposalEntity.find {
                (TransferProposalTable.monthId eq monthId) and
                    (TransferProposalTable.status eq TransferStatus.PENDING)
            }.forEach { it.delete() }
        }
    }

    override suspend fun save(
        monthId: Int,
        creation: TransferProposalCreation,
    ): TransferProposal = transaction {
        TransferProposalEntity.new {
            ticker = creation.ticker
            month = MonthEntity[monthId]
            fromStrategy = StrategyEntity[creation.fromStrategyId]
            toStrategy = StrategyEntity[creation.toStrategyId]
            quantity = creation.quantity
            amount = creation.amount
            autoApproved = creation.autoApproved
            status = creation.status
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    override suspend fun decide(id: Int, status: TransferStatus): TransferProposal? = transaction {
        TransferProposalEntity.findById(id)?.apply { this.status = status }?.toModel()
    }
}
