package dev.agner.portfolio.usecase.order.repository

import dev.agner.portfolio.usecase.order.model.SuggestedOrder
import dev.agner.portfolio.usecase.order.model.SuggestedOrderCreation
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import java.math.BigDecimal

interface ISuggestedOrderRepository {

    suspend fun fetchBy(monthId: Int): List<SuggestedOrder>

    suspend fun fetchById(id: Int): SuggestedOrder?

    // A refresh recomputes the whole month: the previous suggestions are replaced in one
    // transaction so a half-written plan can never be read.
    suspend fun replaceAll(monthId: Int, orders: List<SuggestedOrderCreation>): List<SuggestedOrder>

    // An explicit match override retargets one order at a trade; null clears the match.
    suspend fun updateMatch(
        id: Int,
        matchedTradeId: Int?,
        executedQuantity: BigDecimal,
        status: SuggestedOrderStatus,
    ): SuggestedOrder?
}
