package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.repository.IBondOrderRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

@Service
class BondOrderService(
    private val bondOrderRepository: IBondOrderRepository,
) {
    suspend fun fetchAll() = bondOrderRepository.fetchAll()

    suspend fun fetchByBondId(bondId: Int) = bondOrderRepository.fetchByBondId(bondId)

    suspend fun create(bondId: Int, type: BondOrderType, date: LocalDate, amount: Double) =
        BondOrderCreation(bondId, type, date, amount)
            .run { bondOrderRepository.save(this) }
}
