package dev.agner.portfolio.usecase.upload

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.commons.nextDay
import dev.agner.portfolio.usecase.upload.model.UploadOrder
import kotlinx.coroutines.awaitAll
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.plus
import org.springframework.stereotype.Service

@Service
class UploadService(
    private val bondOrderService: BondOrderService,
) {

    suspend fun createOrders(bondId: Int, orders: List<UploadOrder>) =
        orders
            .map {
                when (it.date.dayOfWeek) {
                    SATURDAY -> it.copy(date = it.date.nextDay().nextDay())
                    SUNDAY -> it.copy(date = it.date.nextDay())
                    else -> it
                }
            }
            .groupBy { it.date }
            .mapValues { x -> x.value.groupBy { it.action } }
            .flatMap { (date, ordersByAction) ->
                ordersByAction.map { (action, orders) ->
                    UploadOrder(date, action, orders.sumOf { it.amount })
                }
            }
            .mapAsync { bondOrderService.create(it.toBondOrderCreation(bondId)) }
            .awaitAll()
}
