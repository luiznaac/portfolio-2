package dev.agner.portfolio.usecase.tax.repository

// The per-ticker harvest refusals. A ticker is either in the set (the owner refused harvesting it)
// or absent (harvest is allowed); removing the row clears the refusal.
interface IHarvestOptOutRepository {

    suspend fun fetchAll(): Set<String>

    suspend fun set(ticker: String, optedOut: Boolean)
}
