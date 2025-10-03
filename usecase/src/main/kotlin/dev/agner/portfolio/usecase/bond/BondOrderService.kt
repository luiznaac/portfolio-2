package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.repository.IBondOrderRepository
import org.springframework.stereotype.Service

@Service
class BondOrderService(
    private val bondOrderRepository: IBondOrderRepository,
) {
    suspend fun fetchAll() = bondOrderRepository.fetchAll()

    suspend fun fetchByBondId(bondId: Int) = bondOrderRepository.fetchByBondId(bondId)

    suspend fun create(bondCreation: BondOrderCreation) = bondOrderRepository.save(bondCreation)
}
