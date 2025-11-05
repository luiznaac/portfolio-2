package dev.agner.portfolio.usecase.upload

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.checkingaccount.CheckingAccountService
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccountMovementCreation
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.commons.toMondayIfWeekend
import dev.agner.portfolio.usecase.upload.model.UploadOrder
import dev.agner.portfolio.usecase.upload.model.UploadOrder.Action.BUY
import dev.agner.portfolio.usecase.upload.model.UploadOrder.Action.SELL
import kotlinx.coroutines.awaitAll
import org.springframework.stereotype.Service

@Service
class UploadService(
    private val bondOrderService: BondOrderService,
    private val checkingAccountService: CheckingAccountService,
) {

    suspend fun createOrders(bondId: Int, orders: List<UploadOrder>) =
        orders
            .map { it.copy(date = it.date.toMondayIfWeekend()) }
            .groupBy { it.date }
            .mapValues { x -> x.value.groupBy { it.action } }
            .flatMap { (date, ordersByAction) ->
                ordersByAction.map { (action, orders) ->
                    UploadOrder(date, action, orders.sumOf { it.amount })
                }
            }
            .mapAsync { bondOrderService.create(it.toBondOrderCreation(bondId)) }
            .awaitAll()

    suspend fun createMovements(checkingAccount: Int, orders: List<UploadOrder>) =
        orders
            .map { it.copy(date = it.date.toMondayIfWeekend()) }
            .groupBy { it.date }
            .mapValues { x -> x.value.groupBy { it.action } }
            .flatMap { (date, ordersByAction) ->
                ordersByAction.map { (action, orders) ->
                    UploadOrder(date, action, orders.sumOf { it.amount })
                }
            }
            .sortedBy { it.date }
            .onEach {
                when (it.action) {
                    BUY -> checkingAccountService.deposit(it.toMovementCreation(checkingAccount))
                    SELL -> checkingAccountService.withdraw(it.toMovementCreation(checkingAccount))
                }
            }
}

private fun UploadOrder.toMovementCreation(checkingAccountId: Int) =
    CheckingAccountMovementCreation(
        checkingAccountId = checkingAccountId,
        date = date,
        amount = amount,
    )
