package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondCreation
import dev.agner.portfolio.usecase.bond.repository.IBondRepository
import org.springframework.stereotype.Service

@Service
class BondService(
    private val bondRepository: IBondRepository,
) {

    suspend fun createBond(creation: BondCreation) = bondRepository.save(creation)

    suspend fun fetchAll() = bondRepository.fetchAll()
}
