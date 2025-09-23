package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.persistence.index.IndexEntity
import dev.agner.portfolio.usecase.bond.model.BondCreation
import dev.agner.portfolio.usecase.bond.model.FixedRateBondCreation
import dev.agner.portfolio.usecase.bond.model.FloatingRateBondCreation
import dev.agner.portfolio.usecase.bond.repository.IBondRepository
import dev.agner.portfolio.usecase.extension.mapToSet
import dev.agner.portfolio.usecase.extension.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component

@Component
class BondRepository : IBondRepository {

    override suspend fun fetchAllBonds() = transaction {
        BondEntity.all().mapToSet { it.toModel() }
    }

    override suspend fun save(creation: BondCreation) = transaction {
        BondEntity.new {
            name = creation.name
            rateType = creation.resolveType()
            value = creation.value.toBigDecimal()
            indexId = if (creation is FloatingRateBondCreation) IndexEntity.findById(creation.indexId.name) else null
            createdAt = LocalDateTime.now()
        }.toModel()
    }
}

private fun BondCreation.resolveType() = when (this) {
    is FixedRateBondCreation -> "FIXED"
    is FloatingRateBondCreation -> "FLOATING"
    else -> throw IllegalStateException("Unknown rate type for model: ${this::class.simpleName}")
}
