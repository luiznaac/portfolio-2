package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.persistence.checkingaccount.CheckingAccountEntity
import dev.agner.portfolio.persistence.index.IndexEntity
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.bond.model.BondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FixedRateBondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FloatingRateBondCreation
import dev.agner.portfolio.usecase.bond.repository.IBondRepository
import dev.agner.portfolio.usecase.commons.mapToSet
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class BondRepository(
    private val clock: Clock,
) : IBondRepository {

    override suspend fun fetchAll() = transaction {
        BondEntity.all().mapToSet { it.toModel() }
    }

    override suspend fun fetchById(bondId: Int) = transaction {
        BondEntity.findById(bondId)?.toModel()
    }

    override suspend fun save(creation: BondCreation) = transaction {
        BondEntity.new {
            name = creation.name
            rateType = creation.resolveType()
            value = creation.value
            maturityDate = creation.maturityDate
            indexId = if (creation is FloatingRateBondCreation) IndexEntity.findById(creation.indexId.name) else null
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    override suspend fun save(checkingAccountId: Int, creation: FloatingRateBondCreation) = transaction {
        BondEntity.new {
            name = creation.name
            rateType = "FLOATING"
            value = creation.value
            maturityDate = creation.maturityDate
            indexId = IndexEntity.findById(creation.indexId.name)
            checkingAccount = CheckingAccountEntity.findById(checkingAccountId)
            createdAt = LocalDateTime.now(clock)
        }.toModel() as FloatingRateBond
    }

    override suspend fun fetchBondsWithoutFullRedemptionOrMaturity() = transaction {
        exec(
            """
            SELECT
                bo.bond_id AS id,
                COALESCE(SUM(yield_result.amount), 0)
                    + COALESCE(SUM(bo.amount), 0)
                    - COALESCE(SUM(principal_redeem.amount), 0) AS result
            FROM bond_order bo
            LEFT JOIN (
                SELECT
                    buy_order_id,
                    SUM(CASE
                        WHEN type = 'YIELD' THEN amount
                        WHEN type = 'YIELD_REDEEM' THEN -amount
                        WHEN type LIKE '%_TAX' THEN -amount
                        ELSE 0
                    END) AS amount
                FROM bond_order_statement
                GROUP BY buy_order_id
            ) yield_result ON yield_result.buy_order_id = bo.id
            LEFT JOIN (
                SELECT
                    buy_order_id,
                    SUM(amount) AS amount
                FROM bond_order_statement
                WHERE type = 'PRINCIPAL_REDEEM'
                GROUP BY buy_order_id
            ) principal_redeem ON principal_redeem.buy_order_id = bo.id
            WHERE checking_account_id IS NULL
                AND bo.type = 'BUY'
            GROUP BY bo.bond_id
            HAVING result > 0;
            """.trimIndent(),
        ) { resultSet ->
            val checkingAccountIds = mutableListOf<Int>()
            while (resultSet.next()) {
                checkingAccountIds.add(resultSet.getInt("id"))
            }
            checkingAccountIds
        }?.let { ids ->
            BondEntity.find { BondTable.id inList ids }.map { it.toModel() }
        } ?: emptyList()
    }
}

private fun BondCreation.resolveType() = when (this) {
    is FixedRateBondCreation -> "FIXED"
    is FloatingRateBondCreation -> "FLOATING"
}
