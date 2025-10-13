package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType.FULL_REDEMPTION
import dev.agner.portfolio.usecase.bond.model.BondOrderType.MATURITY
import dev.agner.portfolio.usecase.bond.repository.IBondOrderRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import kotlin.reflect.KClass

@Service
class BondOrderService(
    private val bondOrderRepository: IBondOrderRepository,
) {

    suspend fun fetchByBondId(bondId: Int) = bondOrderRepository.fetchByBondId(bondId)

    suspend fun create(bondCreation: BondOrderCreation, isInternal: Boolean = false) = with(bondCreation) {
        if (bondCreation.type == FULL_REDEMPTION && bondCreation.amount > BigDecimal("0.00")) {
            throw IllegalArgumentException("Cannot create a full redemption order with a non-zero amount")
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
