package dev.agner.portfolio.usecase.bond.statement

import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import org.springframework.stereotype.Service

@Service
class BondStatementService(
    private val repository: IBondOrderStatementRepository,
) {

    suspend fun fetchAllByBondId(bondId: Int) = repository.fetchAllByBondId(bondId)
}
