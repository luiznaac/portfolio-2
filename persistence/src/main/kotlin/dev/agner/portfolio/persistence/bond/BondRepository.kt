package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.persistence.index.IndexEntity
import dev.agner.portfolio.usecase.bond.model.BondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FixedRateBondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FloatingRateBondCreation
import dev.agner.portfolio.usecase.bond.repository.IBondRepository
import dev.agner.portfolio.usecase.commons.mapToSet
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class BondRepository(
    private val clock: Clock,
) : IBondRepository {

    override suspend fun fetchAll() = transaction {
        BondEntity.all().mapToSet { it.toModel() }
    }

    override suspend fun fetchById(bondId: Int) = transaction {
        BondEntity.findById(bondId)?.toModel()
    }

    override suspend fun save(creation: BondCreation) = transaction {
        BondEntity.new {
            name = creation.name
            rateType = creation.resolveType()
            value = creation.value
            indexId = if (creation is FloatingRateBondCreation) IndexEntity.findById(creation.indexId.name) else null
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }
}

private fun BondCreation.resolveType() = when (this) {
    is FixedRateBondCreation -> "FIXED"
    is FloatingRateBondCreation -> "FLOATING"
}
