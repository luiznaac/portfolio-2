package dev.agner.portfolio.persistence.monthlyclose

import dev.agner.portfolio.usecase.commons.now
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
        MonthlyCloseEntity.find { MonthlyCloseTable.month eq month }.firstOrNull()?.toModel()
            ?: MonthlyCloseEntity.new {
                this.month = month
                status = MonthlyCloseStatus.OPEN
                closedAt = null
                createdAt = LocalDateTime.now(clock)
            }.toModel()
    }

    override suspend fun close(month: LocalDate) = transaction {
        val entity = MonthlyCloseEntity.find { MonthlyCloseTable.month eq month }.firstOrNull()
            ?: throw IllegalStateException("Month $month was never opened")

        require(entity.status == MonthlyCloseStatus.OPEN) { "Month $month is already closed" }

        entity.status = MonthlyCloseStatus.CLOSED
        entity.closedAt = LocalDateTime.now(clock)
        entity.toModel()
    }
}
