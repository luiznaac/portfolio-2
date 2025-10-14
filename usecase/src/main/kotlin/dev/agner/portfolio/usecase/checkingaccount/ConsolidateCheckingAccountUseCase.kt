package dev.agner.portfolio.usecase.checkingaccount

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.BondConsolidationOrchestrator
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Deposit
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullWithdrawal
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Withdrawal
import dev.agner.portfolio.usecase.checkingaccount.repository.ICheckingAccountRepository
import org.springframework.stereotype.Component

@Component
class ConsolidateCheckingAccountUseCase(
    private val bondOrderService: BondOrderService,
    private val repository: ICheckingAccountRepository,
    private val consolidationOrchestrator: BondConsolidationOrchestrator,
) {

    suspend fun execute(checkingAccountId: Int) {
        val orders = bondOrderService.fetchByCheckingAccountId(checkingAccountId)
        val alreadyConsolidatedWithdrawals = repository.fetchAlreadyConsolidatedWithdrawalsIds(checkingAccountId)
        val withdrawalOrders = orders
            .filterIsInstance<Withdrawal>()
            .filterNot { alreadyConsolidatedWithdrawals.contains(it.id) }

        val alreadyRedeemedDeposits = repository.fetchAlreadyRedeemedDepositIds(checkingAccountId)
        val depositOrders = orders
            .filterIsInstance<Deposit>()
            .filterNot { alreadyRedeemedDeposits.contains(it.id) }

        val fullWithdrawalOrder = orders.filterIsInstance<FullWithdrawal>().firstOrNull()

        consolidationOrchestrator.consolidate(depositOrders, withdrawalOrders, fullWithdrawalOrder)
    }
}
