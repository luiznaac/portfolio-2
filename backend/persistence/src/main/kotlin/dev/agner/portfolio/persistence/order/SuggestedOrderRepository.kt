package dev.agner.portfolio.persistence.order

import dev.agner.portfolio.persistence.listedasset.TradeEntity
import dev.agner.portfolio.persistence.portfolio.MonthEntity
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.order.model.SuggestedOrder
import dev.agner.portfolio.usecase.order.model.SuggestedOrderCreation
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import dev.agner.portfolio.usecase.order.repository.ISuggestedOrderRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock

@Component
class SuggestedOrderRepository(
    private val clock: Clock,
) : ISuggestedOrderRepository {

    override suspend fun fetchBy(monthId: Int): List<SuggestedOrder> = transaction {
        SuggestedOrderEntity.find { SuggestedOrderTable.monthId eq monthId }
            .orderBy(SuggestedOrderTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchById(id: Int): SuggestedOrder? = transaction {
        SuggestedOrderEntity.findById(id)?.toModel()
    }

    // Delete + insert in one transaction: a reader sees either the old plan or the new one, never a
    // half-replaced list.
    override suspend fun replaceAll(
        monthId: Int,
        orders: List<SuggestedOrderCreation>,
    ): List<SuggestedOrder> = transaction {
        SuggestedOrderEntity.find { SuggestedOrderTable.monthId eq monthId }.forEach { it.delete() }
        orders.map { creation -> insert(monthId, creation) }
    }

    override suspend fun updateMatch(
        id: Int,
        matchedTradeId: Int?,
        executedQuantity: BigDecimal,
        status: SuggestedOrderStatus,
    ): SuggestedOrder? = transaction {
        SuggestedOrderEntity.findById(id)?.apply {
            this.matchedTrade = matchedTradeId?.let { TradeEntity[it] }
            this.executedQuantity = executedQuantity
            this.status = status
        }?.toModel()
    }

    private fun insert(monthId: Int, creation: SuggestedOrderCreation): SuggestedOrder =
        SuggestedOrderEntity.new {
            ticker = creation.ticker
            side = creation.side
            quantity = creation.quantity
            suggestedPrice = creation.suggestedPrice
            status = creation.status
            month = MonthEntity[monthId]
            matchedTrade = creation.matchedTradeId?.let { TradeEntity[it] }
            executedQuantity = creation.executedQuantity
            createdAt = LocalDateTime.now(clock)
        }.toModel()
}
