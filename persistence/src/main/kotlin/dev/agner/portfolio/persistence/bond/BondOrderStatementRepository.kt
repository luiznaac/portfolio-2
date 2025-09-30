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
        BondOrderStatementEntity.find { BondOrderStatementTable.buyOrderId eq bondOrderId }
            .orderBy(BondOrderStatementTable.date to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toModel()
    }

    override suspend fun sumUpConsolidatedValues(buyOrderId: Int, date: LocalDate) = transaction {
        exec(
            """
                SELECT 
                    SUM(
                        CASE
                            WHEN type = 'YIELD' THEN amount
                            WHEN type = 'YIELD_REDEEM' then -amount
                            WHEN type LIKE '%_TAX' then -amount
                            ELSE 0
                        END
                    ) AS yield_result,
                    SUM(IF(type = 'PRINCIPAL_REDEEM', amount, 0)) AS principal_redeemed
                FROM bond_order_statement
                WHERE buy_order_id = $buyOrderId AND date < '$date';
            """.trimIndent()
        ) {
            if (it.next()) {
                it.getDouble("principal_redeemed") to it.getDouble("yield_result")
            } else {
                0.0 to 0.0
            }
        }!!
    }

    override suspend fun fetchAlreadyRedeemedBuyIdsByOrderId(bondId: Int) = transaction {
        exec(
            """
                SELECT 
                    bo.id, 
                    bo.amount + SUM(IF(sell_order_id IS NULL, bos.amount, -bos.amount)) AS buy_remainder
                FROM bond_order bo
                LEFT JOIN bond_order_statement bos ON bo.id = bos.buy_order_id
                WHERE bo.bond_id = $bondId
                AND bo.type = 'BUY'
                GROUP BY bo.id
                HAVING buy_remainder <= 0.01;
            """.trimIndent()
        ) {
            val ids = mutableSetOf<Int>()

            while (it.next()) {
                ids.add(it.getInt("id"))
            }

            ids
        }!!
    }

    override suspend fun fetchAlreadyConsolidatedSellIdsByOrderId(bondId: Int) = transaction {
        exec(
            """
                SELECT
                    bo.id,
                    bo.amount - COALESCE(SUM(bos.amount), 0) AS sell_remain
                FROM bond_order bo
                LEFT JOIN bond_order_statement bos on bo.id = bos.sell_order_id
                WHERE bond_id = $bondId AND bo.type = 'SELL'
                GROUP BY bo.id
                HAVING sell_remain <= 0.01;
            """.trimIndent()
        ) {
            val ids = mutableSetOf<Int>()

            while (it.next()) {
                ids.add(it.getInt("id"))
            }

            ids
        }!!
    }

    override suspend fun saveAll(creations: List<BondOrderStatementCreation>): Unit = transaction {
        BondOrderStatementTable.batchInsert(creations) {
            this[BondOrderStatementTable.buyOrderId] = it.buyOrderId
            this[BondOrderStatementTable.sellOrderId] = it.resolveSellOrderId()
            this[BondOrderStatementTable.type] = it.resolveType()
            this[BondOrderStatementTable.date] = it.date
            this[BondOrderStatementTable.amount] = it.amount.toBigDecimal()
            this[BondOrderStatementTable.createdAt] = LocalDateTime.now(clock)
        }
    }
}

private fun BondOrderStatementCreation.resolveSellOrderId() = when (this) {
    is BondOrderStatementCreation.Yield -> null
    is BondOrderStatementCreation.YieldRedeem -> sellOrderId
    is BondOrderStatementCreation.PrincipalRedeem -> sellOrderId
    is BondOrderStatementCreation.TaxIncidence -> sellOrderId
}

private fun BondOrderStatementCreation.resolveType() = when (this) {
    is BondOrderStatementCreation.Yield -> "YIELD"
    is BondOrderStatementCreation.YieldRedeem -> "YIELD_REDEEM"
    is BondOrderStatementCreation.PrincipalRedeem -> "PRINCIPAL_REDEEM"
    is BondOrderStatementCreation.TaxIncidence -> "${taxType}_TAX"
}
