package dev.agner.portfolio.persistence.order

import dev.agner.portfolio.persistence.portfolio.MonthEntity
import dev.agner.portfolio.persistence.portfolio.MonthTable
import dev.agner.portfolio.persistence.strategy.StrategyEntity
import dev.agner.portfolio.persistence.strategy.StrategyTable
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

// The month's Attribution Transfer inbox. Bookkeeping only: the row records the two slices, the
// whole units proposed, the R$ amount (the sale it avoids) and the decision. A refresh replaces the
// PENDING rows and keeps the decided trail.
@OptIn(ExperimentalTime::class)
object TransferProposalTable : IntIdTable("transfer_proposal") {
    val ticker = varchar("ticker", 12)
    val monthId = reference("month_id", MonthTable.id)
    val fromStrategyId = reference("from_strategy_id", StrategyTable.id)
    val toStrategyId = reference("to_strategy_id", StrategyTable.id)
    val quantity = decimal("quantity", 18, 6)
    val amount = decimal("amount", 18, 6).nullable()
    val autoApproved = bool("auto_approved")
    val status = enumerationByName<TransferStatus>("status", 20)
    val createdAt = datetime("created_at")
}

@OptIn(ExperimentalTime::class)
class TransferProposalEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TransferProposalEntity>(TransferProposalTable)

    var ticker by TransferProposalTable.ticker
    var month by MonthEntity referencedOn TransferProposalTable.monthId
    var fromStrategy by StrategyEntity referencedOn TransferProposalTable.fromStrategyId
    var toStrategy by StrategyEntity referencedOn TransferProposalTable.toStrategyId
    var quantity by TransferProposalTable.quantity
    var amount by TransferProposalTable.amount
    var autoApproved by TransferProposalTable.autoApproved
    var status by TransferProposalTable.status
    var createdAt by TransferProposalTable.createdAt

    fun toModel() = TransferProposal(
        id = id.value,
        ticker = ticker,
        fromStrategyId = fromStrategy.id.value,
        fromStrategy = fromStrategy.name,
        toStrategyId = toStrategy.id.value,
        toStrategy = toStrategy.name,
        quantity = quantity,
        amount = amount,
        autoApproved = autoApproved,
        status = status,
    )
}
