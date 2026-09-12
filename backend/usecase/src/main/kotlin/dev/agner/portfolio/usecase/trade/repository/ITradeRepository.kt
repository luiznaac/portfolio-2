package dev.agner.portfolio.usecase.trade.repository

import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.model.TradeCreation
import kotlinx.datetime.LocalDate

interface ITradeRepository {

    suspend fun fetchByAssetId(assetId: Int): List<Trade>

    /** Every trade in [start, end], inclusive — for the sale-exemption ceiling meter (usecase/order). */
    suspend fun fetchByDateRange(start: LocalDate, end: LocalDate): List<Trade>

    suspend fun save(creation: TradeCreation): Trade
}
