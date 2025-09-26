package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import dev.agner.portfolio.usecase.extension.mapToSet
import dev.agner.portfolio.usecase.extension.now
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class BondOrderStatementRepository(
    private val clock: Clock,
) : IBondOrderStatementRepository {

    override suspend fun fetchAll() = transaction {
        BondOrderStatementEntity.all().mapToSet { it.toModel() }
    }

    override suspend fun fetchLastByBondOrderId(bondOrderId: Int) = transaction {
        BondOrderStatementEntity.find { BondOrderStatementTable.bondOrderId eq bondOrderId }
            .orderBy(BondOrderStatementTable.date to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toModel()
    }

    override suspend fun sumUpConsolidatedValues(bondOrderId: Int, date: LocalDate) = transaction {
        exec(
            """
                SELECT 
                    SUM(
                        CASE
                            WHEN type = 'YIELD' THEN amount
                            WHEN type = 'YIELD_REDEEM' then -amount
                            ELSE 0
                        END
                    ) AS yield_result,
                    SUM(IF(type = 'PRINCIPAL_REDEEM', amount, 0)) AS principal_redeemed
                FROM bond_oder_statement
                WHERE bond_order_id = $bondOrderId AND date < '$date';
            """.trimIndent()
        ) {
            if (it.next()) {
                it.getDouble("principal_redeemed") to it.getDouble("yield_result")
            } else {
                0.0 to 0.0
            }
        }!!
    }

    override suspend fun saveAll(creations: List<BondOrderStatementCreation>): Unit = transaction {
        BondOrderStatementTable.batchInsert(creations) {
            this[BondOrderStatementTable.bondOrderId] = it.bondOrderId
            this[BondOrderStatementTable.type] = it.resolveType()
            this[BondOrderStatementTable.date] = it.date
            this[BondOrderStatementTable.amount] = it.amount.toBigDecimal()
            this[BondOrderStatementTable.createdAt] = LocalDateTime.now(clock)
        }
    }
}

private fun BondOrderStatementCreation.resolveType() = when (this) {
    is BondOrderStatementCreation.Yield -> "YIELD"
    is BondOrderStatementCreation.YieldRedeem -> "YIELD_REDEEM"
    is BondOrderStatementCreation.PrincipalRedeem -> "PRINCIPAL_REDEEM"
}
