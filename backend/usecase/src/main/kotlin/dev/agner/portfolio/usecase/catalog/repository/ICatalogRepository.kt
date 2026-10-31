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

    // The bulk form of the same write: every classified ticker gets its override in one transaction,
    // and the returned entries are the rows actually written. An unknown ticker is skipped (the
    // caller auto-enters it first); a failure rolls the whole batch back, so no row is half-saved.
    suspend fun setAssetClassOverrides(classifications: Map<String, AssetClass>): List<CatalogEntry>
}
