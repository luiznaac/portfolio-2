package dev.agner.portfolio.persistence.portfolio

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.monthlyclose.SnapshotAlreadyExistsException
import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
import dev.agner.portfolio.usecase.monthlyclose.repository.ISnapshotRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

// Insert-only by construction: there is no update path, and a second snapshot for the same month is
// refused before the unique key can collide, so the error names the domain rule rather than a raw
// constraint violation.
@Component
class SnapshotRepository(
    private val clock: Clock,
) : ISnapshotRepository {

    override suspend fun save(monthId: Int, state: CloseState): Snapshot = transaction {
        if (SnapshotEntity.find { SnapshotTable.monthId eq monthId }.any()) {
            throw SnapshotAlreadyExistsException(monthId)
        }

        SnapshotEntity.new {
            month = MonthEntity[monthId]
            this.state = JsonMapper.mapper.writeValueAsString(state)
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    override suspend fun fetchByMonth(monthId: Int): Snapshot? = transaction {
        SnapshotEntity.find { SnapshotTable.monthId eq monthId }.firstOrNull()?.toModel()
    }
}
