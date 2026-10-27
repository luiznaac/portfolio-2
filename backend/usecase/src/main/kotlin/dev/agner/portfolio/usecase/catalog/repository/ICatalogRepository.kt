package dev.agner.portfolio.usecase.catalog.repository

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry

interface ICatalogRepository {

    // Insert new tickers and move last_seen/descriptive fields forward on existing ones. Never
    // deletes — the returned count is the number of rows written.
    suspend fun upsertAll(entries: List<CatalogEntry>): Int

    suspend fun fetchAll(): List<CatalogEntry>

    suspend fun fetchBy(ticker: String): CatalogEntry?

    // Set or clear the manual class override on an existing row, leaving every other column alone.
    // Null when the ticker is not in the catalog.
    suspend fun setAssetClassOverride(ticker: String, assetClass: AssetClass?): CatalogEntry?
}
