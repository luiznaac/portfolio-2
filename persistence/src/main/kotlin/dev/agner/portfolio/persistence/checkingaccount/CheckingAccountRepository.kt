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
}
