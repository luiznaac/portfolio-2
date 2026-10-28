package dev.agner.portfolio.usecase.listedasset.repository

import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeKey
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import java.math.BigDecimal

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

    // Every trade of one provenance — the backfill reads the OPENING seeds through this.
    suspend fun fetchAllBySource(source: TradeSource): List<Trade>

    // Retires a trade the history has fully replaced (an absorbed OPENING seed). False when no
    // trade has that id.
    suspend fun delete(id: Int): Boolean

    // Rewrites an OPENING seed to the portion the imported history does not cover and records how
    // much has been absorbed, so a rerun does not subtract the same history twice. Null when no
    // trade has that id.
    suspend fun absorb(id: Int, remainingQuantity: BigDecimal, absorbedQuantity: BigDecimal): Trade?
}
