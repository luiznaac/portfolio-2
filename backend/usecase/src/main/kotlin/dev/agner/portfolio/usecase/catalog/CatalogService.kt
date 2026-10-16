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
}
