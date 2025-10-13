package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType.FULL_REDEMPTION
import dev.agner.portfolio.usecase.bond.model.BondOrderType.FULL_WITHDRAWAL
import dev.agner.portfolio.usecase.bond.model.BondOrderType.MATURITY
import dev.agner.portfolio.usecase.bond.repository.IBondOrderRepository
import org.springframework.stereotype.Service
import kotlin.reflect.KClass

@Service
class BondOrderService(
    private val bondOrderRepository: IBondOrderRepository,
) {

    suspend fun fetchByBondId(bondId: Int) = bondOrderRepository.fetchByBondId(bondId)

    suspend fun create(bondCreation: BondOrderCreation, isInternal: Boolean = false) = with(bondCreation) {
        if (listOf(FULL_REDEMPTION, FULL_WITHDRAWAL).contains(bondCreation.type) && bondCreation.amount != null) {
            throw IllegalArgumentException("Cannot create a full redemption order with an amount")
        }

        if (bondCreation.type == MATURITY && !isInternal) {
            throw IllegalArgumentException("Cannot create a maturity order from an external source")
        }

        bondOrderRepository.save(creation = this)
    }

    suspend fun <T : BondOrder> updateType(id: Int, type: KClass<T>) {
        bondOrderRepository.updateType(id, type)
    }
}
