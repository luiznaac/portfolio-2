package dev.agner.portfolio.persistence.portfolio

import dev.agner.portfolio.persistence.order.SuggestedOrderEntity
import dev.agner.portfolio.persistence.order.SuggestedOrderTable
import dev.agner.portfolio.persistence.order.TransferProposalEntity
import dev.agner.portfolio.persistence.order.TransferProposalTable
import dev.agner.portfolio.persistence.tax.HarvestPairEntity
import dev.agner.portfolio.persistence.tax.HarvestPairTable
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.MonthUpdate
import dev.agner.portfolio.usecase.portfolio.repository.IMonthRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import org.jetbrains.exposed.v1.core.SortOrder
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

    override suspend fun fetchAll(): List<Month> = transaction {
        MonthEntity.all()
            .orderBy(MonthTable.period to SortOrder.ASC)
            .map { it.toModel() }
    }

    // Both writes share one transaction: replacing the previous OPEN month and inserting the new one
    // can never be observed half-applied. A backdated open month is plan-only, so it is discarded
    // with its targets instead of being kept as a closed month with no Snapshot; the current month
    // being replaced keeps the existing auto-close-without-Snapshot behaviour.
    override suspend fun open(creation: MonthCreation): Month = transaction {
        val currentPeriod = LocalDate.today(clock).yearMonth
        val openMonths = MonthEntity.find { MonthTable.status eq MonthStatus.OPEN }.toList()
        for (month in openMonths) {
            if (YearMonth.parse(month.period) < currentPeriod) {
                val id = month.id.value
                HarvestPairEntity.find { HarvestPairTable.monthId eq id }.forEach { it.delete() }
                TransferProposalEntity.find { TransferProposalTable.monthId eq id }.forEach { it.delete() }
                SuggestedOrderEntity.find { SuggestedOrderTable.monthId eq id }.forEach { it.delete() }
                TargetEntity.find { TargetTable.monthId eq id }.forEach { it.delete() }
                month.delete()
            } else {
                month.status = MonthStatus.CLOSED
            }
        }

        MonthEntity.new {
            period = creation.period.toString()
            capital = creation.capital
            floor = creation.floor
            aportes = creation.aportes
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
                update.aportes?.let { aportes = it }
            }
            ?.toModel()
    }

    override suspend fun close(id: Int): Month? = transaction {
        MonthEntity.findById(id)
            ?.takeIf { it.status == MonthStatus.OPEN }
            ?.apply { status = MonthStatus.CLOSED }
            ?.toModel()
    }

    override suspend fun fetchClosed(): List<Month> = transaction {
        MonthEntity.find { MonthTable.status eq MonthStatus.CLOSED }
            .orderBy(MonthTable.period to SortOrder.DESC)
            .map { it.toModel() }
    }
}
