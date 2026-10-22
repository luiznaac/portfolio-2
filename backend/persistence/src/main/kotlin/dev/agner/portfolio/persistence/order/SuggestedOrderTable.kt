package dev.agner.portfolio.persistence.order

import dev.agner.portfolio.persistence.listedasset.TradeEntity
import dev.agner.portfolio.persistence.listedasset.TradeTable
import dev.agner.portfolio.persistence.portfolio.MonthEntity
import dev.agner.portfolio.persistence.portfolio.MonthTable
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.SuggestedOrder
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

// The persisted suggested orders of a month. A refresh replaces the month's rows, so the row holds
// the matched execution's evidence (`matched_trade_id`, the first trade the match consumed) and the
// cumulative quantity that decided the lifecycle status.
@OptIn(ExperimentalTime::class)
object SuggestedOrderTable : IntIdTable("suggested_order") {
    val ticker = varchar("ticker", 12)
    val side = enumerationByName<OrderSide>("side", 20)
    val quantity = decimal("quantity", 18, 6)
    val suggestedPrice = decimal("suggested_price", 18, 6).nullable()
    val status = enumerationByName<SuggestedOrderStatus>("status", 20)
    val monthId = reference("month_id", MonthTable.id)
    val matchedTradeId = reference("matched_trade_id", TradeTable.id).nullable()
    val executedQuantity = decimal("executed_quantity", 18, 6)
    val createdAt = datetime("created_at")
}

@OptIn(ExperimentalTime::class)
class SuggestedOrderEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SuggestedOrderEntity>(SuggestedOrderTable)

    var ticker by SuggestedOrderTable.ticker
    var side by SuggestedOrderTable.side
    var quantity by SuggestedOrderTable.quantity
    var suggestedPrice by SuggestedOrderTable.suggestedPrice
    var status by SuggestedOrderTable.status
    var month by MonthEntity referencedOn SuggestedOrderTable.monthId
    var matchedTrade by TradeEntity optionalReferencedOn SuggestedOrderTable.matchedTradeId
    var executedQuantity by SuggestedOrderTable.executedQuantity
    var createdAt by SuggestedOrderTable.createdAt

    fun toModel() = SuggestedOrder(
        id = id.value,
        ticker = ticker,
        side = side,
        quantity = quantity,
        suggestedPrice = suggestedPrice,
        executedQuantity = executedQuantity,
        status = status,
        matchedTradeId = matchedTrade?.id?.value,
    )
}
