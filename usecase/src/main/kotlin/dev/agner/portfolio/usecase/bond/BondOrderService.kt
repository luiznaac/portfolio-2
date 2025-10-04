package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.repository.IBondOrderRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class BondOrderService(
    private val bondOrderRepository: IBondOrderRepository,
) {
    suspend fun fetchAll() = bondOrderRepository.fetchAll()

    suspend fun fetchById(id: Int) = bondOrderRepository.fetchById(id)
        ?: throw IllegalArgumentException("Bond order not found for id $id")

    suspend fun fetchByBondId(bondId: Int) = bondOrderRepository.fetchByBondId(bondId)

    suspend fun create(bondCreation: BondOrderCreation) = bondOrderRepository.save(bondCreation)

    suspend fun updateAmount(id: Int, newAmount: BigDecimal) { bondOrderRepository.updateAmount(id, newAmount) }
}
