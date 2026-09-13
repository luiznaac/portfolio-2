package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.trade.model.Trade
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

fun buy(
    id: Int = 1,
    assetId: Int = 1,
    date: LocalDate,
    quantity: String,
    price: String,
) = Trade.Buy(id, assetId, date, BigDecimal(quantity), BigDecimal(price))

fun sell(
    id: Int = 1,
    assetId: Int = 1,
    date: LocalDate,
    quantity: String,
    price: String,
) = Trade.Sell(id, assetId, date, BigDecimal(quantity), BigDecimal(price))

/**
 * Runs the block inline. Unit tests exercise the composition inside a transaction, not the
 * rollback semantics — those belong to the integration tests, which use the real
 * `TransactionService`.
 */
object PassThroughTransactionTemplate : ITransactionTemplate {
    override suspend fun <T> execute(block: suspend () -> T): T = block()
}
