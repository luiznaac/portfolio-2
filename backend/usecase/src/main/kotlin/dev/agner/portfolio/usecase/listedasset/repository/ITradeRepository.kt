package dev.agner.portfolio.usecase.listedasset.repository

import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation

interface ITradeRepository {

    suspend fun save(creation: TradeCreation): Trade

    suspend fun fetchAll(): List<Trade>

    suspend fun fetchAllBy(ticker: String): List<Trade>
}
