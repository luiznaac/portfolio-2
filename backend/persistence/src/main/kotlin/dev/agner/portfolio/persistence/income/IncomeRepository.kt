package dev.agner.portfolio.persistence.income

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.income.model.Income
import dev.agner.portfolio.usecase.income.model.IncomeCreation
import dev.agner.portfolio.usecase.income.model.IncomeStatus
import dev.agner.portfolio.usecase.income.model.TaxNature
import dev.agner.portfolio.usecase.income.repository.IIncomeRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class IncomeRepository(
    private val clock: Clock,
) : IIncomeRepository {

    // The Income read selects tax_nature = INCOME, newest payable date first, so corporate-action
    // cash stored in the same table never surfaces as a provento.
    override suspend fun fetchAll(): List<Income> = transaction {
        CashMovementEntity.find { CashMovementTable.taxNature eq TaxNature.INCOME }
            .orderBy(CashMovementTable.date to SortOrder.DESC)
            .map { it.toModel() }
    }

    override suspend fun saveAll(creations: List<IncomeCreation>): List<Income> = transaction {
        creations.map { creation ->
            CashMovementEntity.new {
                ticker = creation.ticker
                date = creation.date
                incomeType = creation.type
                grossAmount = creation.grossAmount
                retainedAmount = creation.retainedAmount
                netAmount = creation.netAmount
                taxNature = creation.taxNature
                provenance = creation.provenance
                status = IncomeStatus.ANNOUNCED
                createdAt = LocalDateTime.now(clock)
            }.toModel()
        }
    }

    override suspend fun markReconciled(ids: List<Int>) {
        if (ids.isEmpty()) return
        transaction {
            CashMovementEntity.find { CashMovementTable.id inList ids }
                .forEach { it.status = IncomeStatus.RECONCILED }
        }
    }
}
