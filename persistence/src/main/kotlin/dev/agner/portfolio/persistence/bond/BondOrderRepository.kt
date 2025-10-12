package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.persistence.checkingaccount.CheckingAccountEntity
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.repository.IBondOrderRepository
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class BondOrderRepository(
    private val clock: Clock,
) : IBondOrderRepository {

    override suspend fun fetchByBondId(bondId: Int) = transaction {
        BondOrderEntity.find { BondOrderTable.bondId eq bondId }.map { it.toModel() }
    }

    override suspend fun save(creation: BondOrderCreation) = transaction {
        BondOrderEntity.new {
            bond = creation.bondId?.let { BondEntity.findById(it)!! }
            checkingAccount = creation.checkingAccountId?.let { CheckingAccountEntity.findById(it)!! }
            type = creation.type.name
            date = creation.date
            amount = creation.amount
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    override suspend fun updateType(id: Int, type: BondOrderType) = transaction {
        BondOrderEntity.findByIdAndUpdate(id) {
            it.type = type.name
        }!!.toModel()
    }
}
