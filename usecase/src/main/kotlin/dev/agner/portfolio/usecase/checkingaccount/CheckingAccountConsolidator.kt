package dev.agner.portfolio.usecase.checkingaccount

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.BondConsolidationService
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Deposit
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullWithdrawal
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Withdrawal
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccountConsolidationContext
import dev.agner.portfolio.usecase.checkingaccount.repository.ICheckingAccountRepository
import dev.agner.portfolio.usecase.consolidation.ProductConsolidator
import dev.agner.portfolio.usecase.consolidation.ProductType
import org.springframework.stereotype.Component

@Component
class CheckingAccountConsolidator(
    private val repository: ICheckingAccountRepository,
    private val bondOrderService: BondOrderService,
    private val consolidationOrchestrator: BondConsolidationService,
) : ProductConsolidator<CheckingAccountConsolidationContext> {
    override val type = ProductType.CHECKING_ACCOUNT

    override suspend fun buildContext(productId: Int): CheckingAccountConsolidationContext {
        val orders = bondOrderService.fetchByCheckingAccountId(productId)
        val alreadyConsolidatedWithdrawals = repository.fetchAlreadyConsolidatedWithdrawalsIds(productId)
        val withdrawalOrders = orders
            .filterIsInstance<Withdrawal>()
            .filterNot { alreadyConsolidatedWithdrawals.contains(it.id) }

        val alreadyRedeemedDeposits = repository.fetchAlreadyRedeemedDepositIds(productId)
        val depositOrders = orders
            .filterIsInstance<Deposit>()
            .filterNot { alreadyRedeemedDeposits.contains(it.id) }

        val fullWithdrawalOrder = orders.filterIsInstance<FullWithdrawal>().firstOrNull()

        return CheckingAccountConsolidationContext(depositOrders, withdrawalOrders, fullWithdrawalOrder)
    }

    override suspend fun consolidate(ctx: CheckingAccountConsolidationContext) {
        consolidationOrchestrator.consolidate(ctx.deposits, ctx.withdrawals, ctx.fullWithdrawal)
    }

    override suspend fun getConsolidatableIds(): List<Int> {
        TODO("Not yet implemented")
    }
}
