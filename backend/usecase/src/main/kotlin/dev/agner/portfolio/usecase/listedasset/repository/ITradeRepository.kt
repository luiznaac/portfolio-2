package dev.agner.portfolio.usecase.listedasset.repository

import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeKey
import dev.agner.portfolio.usecase.listedasset.model.TradeSource

interface ITradeRepository {

    // `source` and `ordinal` are server-decided, so they are parameters rather than fields the
    // request could set (mirrors the no-owner-id convention).
    suspend fun save(creation: TradeCreation, source: TradeSource, ordinal: Int): Trade

    suspend fun fetchAll(): List<Trade>

    suspend fun fetchById(id: Int): Trade?

    suspend fun fetchAllBy(ticker: String): List<Trade>

    // Every trade matching the natural tuple, ordered by ordinal ascending — the list's position is
    // the occurrence the reconciliation walks.
    suspend fun fetchByKey(key: TradeKey): List<Trade>

    // Marks a manual trade as confirmed by an import. Null when no trade has that id.
    suspend fun confirmByB3(id: Int): Trade?
}
