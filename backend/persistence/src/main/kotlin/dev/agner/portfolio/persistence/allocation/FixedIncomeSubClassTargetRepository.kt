package dev.agner.portfolio.persistence.allocation

import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClassTargetCreation
import dev.agner.portfolio.usecase.allocation.repository.IFixedIncomeSubClassTargetRepository
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class FixedIncomeSubClassTargetRepository(
    private val clock: Clock,
) : IFixedIncomeSubClassTargetRepository {

    override suspend fun fetchAll() = transaction {
        FixedIncomeSubClassTargetEntity.all()
            .orderBy(FixedIncomeSubClassTargetTable.effectiveFrom to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchCurrent(date: LocalDate) = transaction {
        FixedIncomeSubClassTargetEntity.find { FixedIncomeSubClassTargetTable.effectiveFrom lessEq date }
            .orderBy(FixedIncomeSubClassTargetTable.effectiveFrom to SortOrder.ASC)
            .map { it.toModel() }
            .groupBy { it.subClass }
            .values
            .map { it.last() }
    }

    override suspend fun save(creation: FixedIncomeSubClassTargetCreation) = transaction {
        FixedIncomeSubClassTargetEntity.new {
            subClass = creation.subClass.name
            weight = creation.weight
            effectiveFrom = creation.effectiveFrom
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }
}
