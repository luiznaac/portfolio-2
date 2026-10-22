package dev.agner.portfolio.usecase.catalog

import dev.agner.portfolio.usecase.catalog.gateway.ICotahistSource
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.commons.today
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class CatalogService(
    private val repository: ICatalogRepository,
    private val source: ICotahistSource,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend fun fetchAll() = repository.fetchAll()

    suspend fun fetchBy(ticker: String) = repository.fetchBy(ticker.normalizedTicker())

    // Upsert the catalog from the latest published COTAHIST file. Unknown BDI codes still enter
    // (AssetKind.UNKNOWN) but are logged so the mapping can be extended instead of silently guessed.
    suspend fun refresh(): Int {
        val entries = source.fetchTradingDay().map { it.toCatalogEntry() }

        entries.filter { it.assetKind == AssetKind.UNKNOWN }
            .forEach { log.warn("COTAHIST record {} has unclassified BDI type {}", it.ticker, it.bdiType) }

        return repository.upsertAll(entries)
    }

    // Auto-enter a ticker into the catalog (minimal entry) so imports never block on unknown tickers.
    suspend fun upsertTicker(ticker: String) {
        val normalized = ticker.normalizedTicker()
        val existing = repository.fetchBy(normalized)
        if (existing == null) {
            repository.upsertAll(
                listOf(
                    CatalogEntry(
                        ticker = normalized,
                        shortName = ticker,
                        bdiType = "??",
                        isin = null,
                        assetKind = AssetKind.UNKNOWN,
                        assetClass = null,
                        firstSeen = LocalDate.today(clock),
                        lastSeen = LocalDate.today(clock),
                    ),
                ),
            )
        }
    }

    private val log = logger()

    // A ticker change is an identity remap, not a new paper: the new code inherits the old entry's
    // ISIN, kind and class, while the old code stays resolvable for its historical rows. The new
    // ticker's first sighting is today; a ticker equal to the old one is a no-op.
    suspend fun remapTicker(from: String, to: String) {
        val old = from.normalizedTicker()
        val new = to.normalizedTicker()
        if (old == new) return
        val source = repository.fetchBy(old) ?: return
        val today = LocalDate.today(clock)
        repository.upsertAll(
            listOf(
                source.copy(ticker = new, firstSeen = today, lastSeen = today),
                // The identity moves with the new code: the old row stays resolvable for its
                // historical trades but no longer claims the ISIN.
                source.copy(isin = null, lastSeen = today),
            ),
        )
    }
}
