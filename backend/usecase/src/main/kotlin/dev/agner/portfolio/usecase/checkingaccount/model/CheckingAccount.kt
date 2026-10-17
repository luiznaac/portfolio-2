package dev.agner.portfolio.usecase.checkingaccount.model

import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeRate
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.index.model.IndexId
import kotlinx.datetime.DatePeriod
import java.math.BigDecimal

data class CheckingAccount(
    val id: Int,
    val name: String,
    val rate: BigDecimal,
    val indexId: IndexId,
    val maturityDuration: DatePeriod,
) {
    // A checking account is always index-linked, so its rate always floats (see FixedIncomeRate).
    val subClass: FixedIncomeSubClass = FixedIncomeRate.Floating(indexId).subClass
}

data class CheckingAccountCreation(
    val name: String,
    val rate: BigDecimal,
    val indexId: IndexId,
    val maturityDuration: DatePeriod,
)
