package dev.agner.portfolio.usecase.listedasset.catalog

import dev.agner.portfolio.usecase.listedasset.catalog.gateway.ITickerCatalogGateway
import dev.agner.portfolio.usecase.listedasset.catalog.repository.ITickerCatalogRepository
import org.springframework.stereotype.Service

@Service
class TickerCatalogSyncService(
    private val gateway: ITickerCatalogGateway,
    private val repository: ITickerCatalogRepository,
) {

    suspend fun search(query: String, limit: Int = 20) = repository.search(query, limit)

    // Called once at boot (see StartupTickerCatalogSync) — a no-op after the first successful
    // sync, so restarts don't re-fetch B3's whole ~2000-ticker universe every time.
    suspend fun syncIfEmpty(): Int = if (repository.count() > 0L) 0 else sync()

    // Also exposed as POST /ticker-catalog/sync so it can be re-run on demand to pick up new
    // listings (IPOs, new FIIs/ETFs) — upsert is additive, existing rows are left alone.
    suspend fun sync(): Int {
        val entries = gateway.fetchAll()
        repository.upsertAll(entries)
        return entries.size
    }
}
