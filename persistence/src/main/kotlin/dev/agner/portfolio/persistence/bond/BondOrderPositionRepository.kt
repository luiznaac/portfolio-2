package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.usecase.bond.position.model.BondOrderPosition
import dev.agner.portfolio.usecase.bond.position.repository.IBondOrderPositionRepository
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class BondOrderPositionRepository(
    private val clock: Clock,
) : IBondOrderPositionRepository {

    override suspend fun saveAll(positions: List<BondOrderPosition>) {
        transaction {
            BondOrderPositionTable.batchInsert(positions) { pos ->
                this[BondOrderPositionTable.bondOrderId] = pos.bondOrderId
                this[BondOrderPositionTable.date] = pos.date
                this[BondOrderPositionTable.principal] = pos.principal
                this[BondOrderPositionTable.yieldValue] = pos.yield
                this[BondOrderPositionTable.taxes] = pos.taxes
                this[BondOrderPositionTable.createdAt] = LocalDateTime.now(clock)
            }
        }
    }

    override suspend fun fetchByBondId(bondId: Int) = transaction {
        BondOrderPositionTable
            .join(BondOrderTable, JoinType.LEFT) { BondOrderPositionTable.bondOrderId eq BondOrderTable.id }
            .select(BondOrderPositionTable.columns)
            .where { BondOrderTable.bondId eq bondId }
            .orderBy(BondOrderPositionTable.date to SortOrder.ASC)
            .map { row -> BondOrderPositionEntity.wrapRow(row).toModel() }
    }

    override suspend fun fetchLastByBondOrderId(bondOrderId: List<Int>) = transaction {
        val bondOrderIds = bondOrderId.joinToString(",")

        exec(
            """
            SELECT bop.*
            FROM bond_order_position bop
            INNER JOIN (
                SELECT
                    bond_order_id,
                    MAX(date) AS last_date
                FROM bond_order_position
                WHERE bond_order_id IN ($bondOrderIds)
                GROUP BY bond_order_id
            ) latest ON bop.bond_order_id = latest.bond_order_id
                    AND bop.date = latest.last_date
            """.trimIndent(),
        ) { rs ->
            val results = mutableListOf<BondOrderPosition>()
            while (rs.next()) {
                results.add(
                    BondOrderPosition(
                        bondOrderId = rs.getInt("bond_order_id"),
                        date = rs.getDate("date").toLocalDate().toKotlinLocalDate(),
                        principal = rs.getBigDecimal("principal"),
                        yield = rs.getBigDecimal("yield"),
                        taxes = rs.getBigDecimal("taxes"),
                    ),
                )
            }
            results
        } ?: emptyList()
    }
}
