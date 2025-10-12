package dev.agner.portfolio.persistence.checkingaccount

import dev.agner.portfolio.persistence.index.IndexEntity
import dev.agner.portfolio.persistence.index.IndexTable
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccount
import dev.agner.portfolio.usecase.index.model.IndexId
import kotlinx.datetime.DatePeriod
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object CheckingAccountTable : IntIdTable("checking_account") {
    val name = varchar("name", 100)
    val value = decimal("value", 8, 4)
    val index = reference("index_id", IndexTable.id)
    val maturityDuration = varchar("maturity_duration", 5)
    val createdAt = datetime("created_at")
}

class CheckingAccountEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CheckingAccountEntity>(CheckingAccountTable)

    var name by CheckingAccountTable.name
    var value by CheckingAccountTable.value
    var index by IndexEntity referencedOn CheckingAccountTable.index
    var maturityDuration by CheckingAccountTable.maturityDuration
    var createdAt by CheckingAccountTable.createdAt

    fun toModel() = CheckingAccount(
        id = id.value,
        name = name,
        value = value,
        indexId = IndexId.valueOf(index.id.value),
        maturityDuration = DatePeriod.parse(maturityDuration),
    )
}
