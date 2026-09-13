package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType.BUY
import dev.agner.portfolio.usecase.bond.model.BondOrderType.DEPOSIT
import dev.agner.portfolio.usecase.bond.model.BondOrderType.FULL_REDEMPTION
import dev.agner.portfolio.usecase.bond.model.BondOrderType.FULL_WITHDRAWAL
import dev.agner.portfolio.usecase.bond.model.BondOrderType.MATURITY
import dev.agner.portfolio.usecase.bond.model.BondOrderType.SELL
import dev.agner.portfolio.usecase.bond.model.BondOrderType.WITHDRAWAL
import dev.agner.portfolio.usecase.bond.repository.IBondOrderRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import kotlin.reflect.KClass

@Service
class BondOrderService(
    private val bondOrderRepository: IBondOrderRepository,
) {

    suspend fun fetchByBondId(bondId: Int) = bondOrderRepository.fetchByBondId(bondId)

    suspend fun create(bondCreation: BondOrderCreation, isInternal: Boolean = false) = with(bondCreation) {
        require(bondCreation.type !in listOf(FULL_REDEMPTION, FULL_WITHDRAWAL) || bondCreation.amount == null) {
            "Cannot create a full redemption order with an amount"
        }

        require(bondCreation.type != MATURITY || isInternal) {
            "Cannot create a maturity order from an external source"
        }

        // Consolidation keys redemptions by date, so a second redemption on the same date would
        // collapse into one and silently never consolidate; reject it at creation instead.
        when (bondCreation.type) {
            SELL -> rejectDuplicateBondRedemption(bondCreation)
            WITHDRAWAL -> rejectDuplicateWithdrawal(bondCreation)
            BUY, DEPOSIT, FULL_REDEMPTION, MATURITY, FULL_WITHDRAWAL -> Unit
        }

        bondOrderRepository.save(creation = this)
    }

    private suspend fun rejectDuplicateBondRedemption(creation: BondOrderCreation) {
        val bondId = creation.bondId ?: return
        if (bondOrderRepository.fetchByBondId(bondId).hasRedemptionOn(creation.date)) {
            throw DuplicateRedemptionException(creation.date, "bond", bondId)
        }
    }

    private suspend fun rejectDuplicateWithdrawal(creation: BondOrderCreation) {
        val checkingAccountId = creation.checkingAccountId ?: return
        if (bondOrderRepository.fetchByCheckingAccountId(checkingAccountId).hasRedemptionOn(creation.date)) {
            throw DuplicateRedemptionException(creation.date, "checking account", checkingAccountId)
        }
    }

    private fun List<BondOrder>.hasRedemptionOn(date: LocalDate) =
        any { it is BondOrder.Redemption && it.date == date }

    suspend fun <T : BondOrder> updateType(id: Int, type: KClass<T>) {
        bondOrderRepository.updateType(id, type)
    }

    suspend fun fetchByCheckingAccountId(checkingAccountId: Int) =
        bondOrderRepository.fetchByCheckingAccountId(checkingAccountId)
}
