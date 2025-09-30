package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondCreation.FixedRateBondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FloatingRateBondCreation
import dev.agner.portfolio.usecase.bond.repository.IBondRepository
import dev.agner.portfolio.usecase.index.model.IndexId
import org.springframework.stereotype.Service

@Service
class BondService(
    private val bondRepository: IBondRepository,
) {

    suspend fun createFixedRateBond(name: String, value: Double) =
        FixedRateBondCreation(name, value)
            .run { bondRepository.save(this) }

    suspend fun createFloatingRateBond(name: String, value: Double, indexId: IndexId) =
        FloatingRateBondCreation(name, value, indexId)
            .run { bondRepository.save(this) }

    suspend fun fetchAll() = bondRepository.fetchAll()
}
