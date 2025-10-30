package dev.agner.portfolio.persistence.checkingaccount

import dev.agner.portfolio.persistence.index.IndexEntity
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccountCreation
import dev.agner.portfolio.usecase.checkingaccount.repository.ICheckingAccountRepository
import dev.agner.portfolio.usecase.commons.mapToSet
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class CheckingAccountRepository(
    private val clock: Clock,
) : ICheckingAccountRepository {

    override suspend fun fetchAll() = transaction {
        CheckingAccountEntity.all().mapToSet { it.toModel() }
    }

    override suspend fun fetchById(checkingAccountId: Int) = transaction {
        CheckingAccountEntity.findById(checkingAccountId)?.toModel()
    }

    override suspend fun save(creation: CheckingAccountCreation) = transaction {
        CheckingAccountEntity.new {
            name = creation.name
            value = creation.value
            index = IndexEntity.findById(creation.indexId.name)!!
            maturityDuration = creation.maturityDuration.toString()
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    override suspend fun fetchAlreadyConsolidatedWithdrawalsIds(checkingAccountId: Int) = transaction {
        exec(
            """
                SELECT
                    bo.id,
                    bo.amount - COALESCE(SUM(bos.amount), 0) AS withdrawal_remain
                FROM bond_order bo
                LEFT JOIN bond_order_statement bos on bo.id = bos.sell_order_id
                WHERE bo.checking_account_id = $checkingAccountId AND bo.type = 'WITHDRAWAL'
                GROUP BY bo.id
                HAVING withdrawal_remain <= 0.00;
            """.trimIndent(),
        ) {
            val ids = mutableSetOf<Int>()

            while (it.next()) {
                ids.add(it.getInt("id"))
            }

            ids
        }!!
    }

    override suspend fun fetchAlreadyRedeemedDepositIds(checkingAccountId: Int) = transaction {
        exec(
            """
                SELECT 
                    bo.id, 
                    bo.amount + SUM(IF(sell_order_id IS NULL, bos.amount, -bos.amount)) AS deposit_remainder
                FROM bond_order bo
                LEFT JOIN bond_order_statement bos ON bo.id = bos.buy_order_id
                WHERE bo.checking_account_id = $checkingAccountId
                AND bo.type = 'DEPOSIT'
                GROUP BY bo.id
                HAVING deposit_remainder <= 0.00;
            """.trimIndent(),
        ) {
            val ids = mutableSetOf<Int>()

            while (it.next()) {
                ids.add(it.getInt("id"))
            }

            ids
        }!!
    }

    override suspend fun fetchCheckingAccountsWithoutFullWithdrawal() = transaction {
        exec(
            """
            SELECT
                ca.id,
                COALESCE(SUM(yield_result.amount), 0)
                    + COALESCE(SUM(bo.amount), 0)
                    - COALESCE(SUM(principal_redeem.amount), 0) AS result
            FROM checking_account ca
            LEFT JOIN bond_order bo ON bo.checking_account_id = ca.id
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
            WHERE bo.type = 'DEPOSIT'
            GROUP BY ca.id
            HAVING result > 0;
            """.trimIndent(),
        ) { resultSet ->
            val checkingAccountIds = mutableListOf<Int>()
            while (resultSet.next()) {
                checkingAccountIds.add(resultSet.getInt("id"))
            }
            checkingAccountIds
        }?.let { ids ->
            CheckingAccountEntity.find { CheckingAccountTable.id inList ids }.map { it.toModel() }
        } ?: emptyList()
    }
}
