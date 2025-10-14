package dev.agner.portfolio.persistence.checkingaccount

import dev.agner.portfolio.persistence.index.IndexEntity
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccountCreation
import dev.agner.portfolio.usecase.checkingaccount.repository.ICheckingAccountRepository
import dev.agner.portfolio.usecase.commons.mapToSet
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDateTime
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
}
