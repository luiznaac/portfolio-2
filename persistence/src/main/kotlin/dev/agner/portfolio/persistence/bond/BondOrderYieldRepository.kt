package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.repository.IBondOrderYieldRepository
import dev.agner.portfolio.usecase.extension.mapToSet
import dev.agner.portfolio.usecase.extension.now
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class BondOrderYieldRepository(
    private val clock: Clock,
) : IBondOrderYieldRepository {

    override suspend fun fetchAll() = transaction {
        BondOrderYieldEntity.all().mapToSet { it.toModel() }
    }

    override suspend fun fetchLastByBondOrderId(bondOrderId: Int) = transaction {
        BondOrderYieldEntity.find { BondOrderYieldTable.bondOrderId eq bondOrderId }
            .orderBy(BondOrderYieldTable.date to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toModel()
    }

    override suspend fun sumYieldUntil(bondOrderId: Int, date: LocalDate) = transaction {
        val amountSum = BondOrderYieldTable.amount.sum()

        BondOrderYieldTable
            .select(amountSum)
            .where {
                (BondOrderYieldTable.bondOrderId eq bondOrderId) and (BondOrderYieldTable.date less date)
            }
            .singleOrNull()
            ?.get(amountSum)
            ?.toDouble()
    }

    override suspend fun saveAll(creations: List<BondOrderStatementCreation>): Unit = transaction {
        BondOrderYieldTable.batchInsert(creations, ignore = true) {
            this[BondOrderYieldTable.bondOrderId] = it.bondOrderId
            this[BondOrderYieldTable.date] = it.date
            this[BondOrderYieldTable.amount] = it.amount.toBigDecimal()
            this[BondOrderYieldTable.createdAt] = LocalDateTime.now(clock)
        }
    }
}
