package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.persistence.checkingaccount.CheckingAccountEntity
import dev.agner.portfolio.persistence.index.IndexEntity
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.bond.model.BondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FixedRateBondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FloatingRateBondCreation
import dev.agner.portfolio.usecase.bond.repository.IBondRepository
import dev.agner.portfolio.usecase.commons.mapToSet
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.jdbc.select
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
            maturityDate = creation.maturityDate
            indexId = if (creation is FloatingRateBondCreation) IndexEntity.findById(creation.indexId.name) else null
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    override suspend fun save(checkingAccountId: Int, creation: FloatingRateBondCreation) = transaction {
        BondEntity.new {
            name = creation.name
            rateType = "FLOATING"
            value = creation.value
            maturityDate = creation.maturityDate
            indexId = IndexEntity.findById(creation.indexId.name)
            checkingAccount = CheckingAccountEntity.findById(checkingAccountId)
            createdAt = LocalDateTime.now(clock)
        }.toModel() as FloatingRateBond
    }

    override suspend fun fetchBondsWithoutFullRedemptionOrMaturity() = transaction {
        val subQuery = BondOrderTable
            .select(BondOrderTable.bondId)
            .where {
                (BondOrderTable.checkingAccountId.isNull()) and
                    (BondOrderTable.type inList listOf("FULL_REDEMPTION", "MATURITY"))
            }

        BondEntity.find {
            (BondTable.checkingAccount.isNull()) and
                (BondTable.id notInSubQuery subQuery)
        }.map { it.toModel() }
    }
}

private fun BondCreation.resolveType() = when (this) {
    is FixedRateBondCreation -> "FIXED"
    is FloatingRateBondCreation -> "FLOATING"
}
