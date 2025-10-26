package dev.agner.portfolio.usecase.checkingaccount.model

import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Deposit
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullWithdrawal
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Withdrawal

data class CheckingAccountConsolidationContext(
    val deposits: List<Deposit>,
    val withdrawals: List<Withdrawal>,
    val fullWithdrawal: FullWithdrawal?,
)
