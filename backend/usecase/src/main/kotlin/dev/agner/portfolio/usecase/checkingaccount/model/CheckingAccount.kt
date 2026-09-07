package dev.agner.portfolio.usecase.checkingaccount.model

import dev.agner.portfolio.usecase.index.model.IndexId
import kotlinx.datetime.DatePeriod
import java.math.BigDecimal

data class CheckingAccount(
    val id: Int,
    val name: String,
    val value: BigDecimal,
    val indexId: IndexId,
    val maturityDuration: DatePeriod,
)

data class CheckingAccountCreation(
    val name: String,
    val value: BigDecimal,
    val indexId: IndexId,
    val maturityDuration: DatePeriod,
)
