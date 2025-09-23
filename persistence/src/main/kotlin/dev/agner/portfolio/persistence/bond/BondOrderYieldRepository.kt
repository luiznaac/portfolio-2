package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.usecase.bond.model.BondOrderYieldCreation
import dev.agner.portfolio.usecase.bond.repository.IBondOrderYieldRepository
import dev.agner.portfolio.usecase.extension.mapToSet
import dev.agner.portfolio.usecase.extension.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.batchInsert
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

    override suspend fun saveAll(bondOrderId: Int, creations: List<BondOrderYieldCreation>): Unit = transaction {
        BondOrderYieldTable.batchInsert(creations) {
            this[BondOrderYieldTable.bondOrderId] = bondOrderId
            this[BondOrderYieldTable.date] = it.date
            this[BondOrderYieldTable.amount] = it.amount.toBigDecimal()
            this[BondOrderYieldTable.createdAt] = LocalDateTime.now(clock)
        }
    }
}
