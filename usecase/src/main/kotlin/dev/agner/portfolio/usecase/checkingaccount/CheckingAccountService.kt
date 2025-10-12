package dev.agner.portfolio.usecase.checkingaccount

import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccountCreation
import dev.agner.portfolio.usecase.checkingaccount.repository.ICheckingAccountRepository
import org.springframework.stereotype.Service

@Service
class CheckingAccountService(
    private val repository: ICheckingAccountRepository,
) {

    suspend fun create(creation: CheckingAccountCreation) = with(creation) {
        repository.save(this)
    }

    suspend fun fetchAll() = repository.fetchAll()
}
