package dev.agner.portfolio.usecase.listedasset.position.repository

import dev.agner.portfolio.usecase.listedasset.position.model.ListedAssetPosition

interface IListedAssetPositionRepository {

    suspend fun fetchByAssetId(assetId: Int): List<ListedAssetPosition>

    /** Upserts by (assetId, date) — re-consolidating the same day replaces that day's position. */
    suspend fun save(assetId: Int, position: ListedAssetPosition)
}
