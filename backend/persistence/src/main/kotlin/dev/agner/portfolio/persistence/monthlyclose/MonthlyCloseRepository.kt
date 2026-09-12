package dev.agner.portfolio.persistence.monthlyclose

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.monthlyclose.MonthlyCloseAlreadyClosedException
import dev.agner.portfolio.usecase.monthlyclose.model.MonthlyCloseStatus
import dev.agner.portfolio.usecase.monthlyclose.repository.IMonthlyCloseRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class MonthlyCloseRepository(
    private val clock: Clock,
) : IMonthlyCloseRepository {

    override suspend fun fetchByMonth(month: LocalDate) = transaction {
        MonthlyCloseEntity.find { MonthlyCloseTable.month eq month }.firstOrNull()?.toModel()
    }

    override suspend fun fetchAll() = transaction {
        MonthlyCloseEntity.all().sortedByDescending { it.month }.map { it.toModel() }
    }

    override suspend fun open(month: LocalDate) = transaction {
        findOrCreate(month).toModel()
    }

    override suspend fun close(month: LocalDate) = transaction {
        val entity = findOrCreate(month)

        if (entity.status == MonthlyCloseStatus.CLOSED) {
            throw MonthlyCloseAlreadyClosedException(month)
        }

        entity.status = MonthlyCloseStatus.CLOSED
        entity.closedAt = LocalDateTime.now(clock)
        entity.toModel()
    }

    // Find-or-create inside the caller's transaction: a never-opened month can be opened and closed
    // atomically, and a concurrent close sees the same row instead of failing on a missing one.
    private fun findOrCreate(month: LocalDate): MonthlyCloseEntity =
        MonthlyCloseEntity.find { MonthlyCloseTable.month eq month }.firstOrNull()
            ?: MonthlyCloseEntity.new {
                this.month = month
                status = MonthlyCloseStatus.OPEN
                closedAt = null
                createdAt = LocalDateTime.now(clock)
            }
}
