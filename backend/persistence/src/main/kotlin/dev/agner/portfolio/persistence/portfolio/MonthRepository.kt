package dev.agner.portfolio.persistence.portfolio

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.MonthUpdate
import dev.agner.portfolio.usecase.portfolio.repository.IMonthRepository
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class MonthRepository(
    private val clock: Clock,
) : IMonthRepository {

    override suspend fun fetchOpen(): Month? = transaction {
        MonthEntity.find { MonthTable.status eq MonthStatus.OPEN }.firstOrNull()?.toModel()
    }

    override suspend fun fetchByPeriod(period: YearMonth): Month? = transaction {
        MonthEntity.find { MonthTable.period eq period.toString() }.firstOrNull()?.toModel()
    }

    // Both writes share one transaction: closing the previous OPEN month and inserting the new one
    // can never be observed half-applied.
    override suspend fun open(creation: MonthCreation): Month = transaction {
        val openMonths = MonthEntity.find { MonthTable.status eq MonthStatus.OPEN }.toList()
        for (month in openMonths) {
            month.status = MonthStatus.CLOSED
        }

        MonthEntity.new {
            period = creation.period.toString()
            capital = creation.capital
            floor = creation.floor
            status = MonthStatus.OPEN
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    override suspend fun updateCurrent(update: MonthUpdate): Month? = transaction {
        MonthEntity.find { MonthTable.status eq MonthStatus.OPEN }
            .firstOrNull()
            ?.apply {
                capital = update.capital
                floor = update.floor
            }
            ?.toModel()
    }
}
