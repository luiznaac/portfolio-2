package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FloatingRateBondCreation
import dev.agner.portfolio.usecase.bond.repository.IBondRepository
import dev.agner.portfolio.usecase.commons.isWeekend
import dev.agner.portfolio.usecase.commons.toMondayIfWeekend
import org.springframework.stereotype.Service

@Service
class BondService(
    private val bondRepository: IBondRepository,
) {

    suspend fun create(creation: BondCreation) = with(creation) {
        if (creation.maturityDate.isWeekend()) {
            throw IllegalArgumentException("Cannot create a bond with a weekend maturity date")
        }

        bondRepository.save(this)
    }

    suspend fun createForCheckingAccount(checkingAccountId: Int, creation: FloatingRateBondCreation) = with(creation) {
        bondRepository.save(checkingAccountId, copy(maturityDate = maturityDate.toMondayIfWeekend()))
    }

    suspend fun fetchAll() = bondRepository.fetchAll()
}
