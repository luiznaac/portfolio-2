package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondCreation
import dev.agner.portfolio.usecase.bond.repository.IBondRepository
import dev.agner.portfolio.usecase.commons.isWeekend
import org.springframework.stereotype.Service

@Service
class BondService(
    private val bondRepository: IBondRepository,
) {

    suspend fun createBond(creation: BondCreation) = with(creation) {
        if (creation.maturityDate.isWeekend()) {
            throw IllegalArgumentException("Cannot create a bond with a weekend maturity date")
        }

        bondRepository.save(this)
    }

    suspend fun fetchAll() = bondRepository.fetchAll()
}
