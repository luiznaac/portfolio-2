package dev.agner.portfolio.persistence.bond

import dev.agner.portfolio.persistence.checkingaccount.CheckingAccountEntity
import dev.agner.portfolio.persistence.checkingaccount.CheckingAccountTable
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Buy
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Deposit
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullRedemption
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullWithdrawal
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.Maturity
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Sell
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Withdrawal
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object BondOrderTable : IntIdTable("bond_order") {
    val bondId = reference("bond_id", BondTable.id).nullable()
    val checkingAccountId = reference("checking_account_id", CheckingAccountTable.id).nullable()
    val type = varchar("type", 20)
    val date = date("date")
    val amount = decimal("amount", 12, 2).nullable()
    val createdAt = datetime("created_at")
}

class BondOrderEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BondOrderEntity>(BondOrderTable)

    var bond by BondEntity optionalReferencedOn BondOrderTable.bondId
    var checkingAccount by CheckingAccountEntity optionalReferencedOn BondOrderTable.checkingAccountId
    var type by BondOrderTable.type
    var date by BondOrderTable.date
    var amount by BondOrderTable.amount
    var createdAt by BondOrderTable.createdAt

    fun toModel() = when (type) {
        "BUY" -> Buy(
            id = id.value,
            date = date,
            bond = bond!!.toModel(),
            amount = amount!!,
        )
        "SELL" -> Sell(
            id = id.value,
            date = date,
            bond = bond!!.toModel(),
            amount = amount!!,
        )
        "FULL_REDEMPTION" -> FullRedemption(
            id = id.value,
            date = date,
            bond = bond!!.toModel(),
        )
        "MATURITY" -> Maturity(
            id = id.value,
            date = date,
            bond = bond!!.toModel(),
        )
        "DEPOSIT" -> Deposit(
            id = id.value,
            date = date,
            amount = amount!!,
            bond = bond!!.toModel(),
            checkingAccountId = checkingAccount!!.id.value,
        )
        "WITHDRAWAL" -> Withdrawal(
            id = id.value,
            date = date,
            amount = amount!!,
            checkingAccountId = checkingAccount!!.id.value,
        )
        "FULL_WITHDRAWAL" -> FullWithdrawal(
            id = id.value,
            date = date,
            checkingAccountId = checkingAccount!!.id.value,
        )
        else -> throw IllegalStateException("Unknown order type: $type")
    }
}
