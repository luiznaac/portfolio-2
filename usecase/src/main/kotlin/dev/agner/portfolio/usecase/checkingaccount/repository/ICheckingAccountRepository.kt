package dev.agner.portfolio.usecase.checkingaccount.repository

import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccount
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccountCreation

interface ICheckingAccountRepository {

    suspend fun fetchAll(): Set<CheckingAccount>

    suspend fun fetchById(checkingAccountId: Int): CheckingAccount?

    suspend fun save(creation: CheckingAccountCreation): CheckingAccount
}
