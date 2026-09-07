package dev.agner.portfolio.usecase.configuration

interface ITransactionTemplate {

    suspend fun <T> execute(block: suspend () -> T): T
}
