package dev.agner.portfolio.usecase.listedasset.repository

import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.model.ListedAssetCreation
import kotlinx.datetime.LocalDate

interface IListedAssetRepository {

    suspend fun fetchAll(): List<ListedAsset>

    suspend fun fetchById(id: Int): ListedAsset?

    suspend fun save(creation: ListedAssetCreation): ListedAsset

    /**
     * Closes the current ticker-history entry and opens a new one.
     * See [dev.agner.portfolio.usecase.listedasset.model.TickerHistoryEntry].
     */
    suspend fun changeTicker(assetId: Int, newTicker: String, effectiveFrom: LocalDate): ListedAsset

    /** Resolves the asset a ticker pointed to on a given date — for reconciling old brokerage notes. */
    suspend fun resolveIdByTicker(ticker: String, date: LocalDate): Int?
}
