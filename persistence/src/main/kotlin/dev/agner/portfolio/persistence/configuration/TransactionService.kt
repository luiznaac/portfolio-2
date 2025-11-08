package dev.agner.portfolio.persistence.configuration

import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component

@Component
class TransactionService(
    private val db: Database,
) : ITransactionTemplate {

    override suspend fun <T> execute(block: suspend () -> T): T = transaction(db) {
        runBlocking { block() }
    }
}
