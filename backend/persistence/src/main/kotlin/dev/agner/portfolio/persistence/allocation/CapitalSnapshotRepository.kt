package dev.agner.portfolio.persistence.allocation

import dev.agner.portfolio.usecase.allocation.model.CapitalSnapshotCreation
import dev.agner.portfolio.usecase.allocation.repository.ICapitalSnapshotRepository
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class CapitalSnapshotRepository(
    private val clock: Clock,
) : ICapitalSnapshotRepository {

    override suspend fun fetchAll() = transaction {
        CapitalSnapshotEntity.all()
            .orderBy(CapitalSnapshotTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchLast() = transaction {
        CapitalSnapshotEntity.all()
            .orderBy(CapitalSnapshotTable.date to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toModel()
    }

    override suspend fun save(creation: CapitalSnapshotCreation) = transaction {
        CapitalSnapshotEntity.new {
            date = creation.date
            externalBalance = creation.externalBalance
            plannedContribution = creation.plannedContribution
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }
}
