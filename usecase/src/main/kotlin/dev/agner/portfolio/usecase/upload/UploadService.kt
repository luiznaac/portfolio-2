package dev.agner.portfolio.usecase.upload

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.upload.model.KinvoOrder
import kotlinx.coroutines.awaitAll
import org.springframework.stereotype.Service

@Service
class UploadService(
    private val bondOrderService: BondOrderService,
) {

    suspend fun createOrders(bondId: Int, orders: List<KinvoOrder>) =
        orders
            .groupBy { it.date }
            .mapValues { x -> x.value.groupBy { it.action } }
            .flatMap { (date, ordersByAction) ->
                ordersByAction.map { (action, orders) ->
                    KinvoOrder(date, action, orders.sumOf { it.amount })
                }
            }
            .mapAsync { bondOrderService.create(it.toBondOrderCreation(bondId)) }
            .awaitAll()
}
