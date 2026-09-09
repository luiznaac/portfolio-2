package dev.agner.portfolio.usecase.trade.repository

import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.model.TradeCreation

interface ITradeRepository {

    suspend fun fetchByAssetId(assetId: Int): List<Trade>

    /** Every trade across every asset — for the sale-exemption ceiling meter (usecase/order). */
    suspend fun fetchAll(): List<Trade>

    suspend fun save(creation: TradeCreation): Trade
}
