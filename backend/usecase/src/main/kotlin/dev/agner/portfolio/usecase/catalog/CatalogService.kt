package dev.agner.portfolio.usecase.catalog

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import dev.agner.portfolio.usecase.catalog.model.UNCATALOGED_BDI_TYPE
import dev.agner.portfolio.usecase.catalog.model.canonicalEntryByTicker
import dev.agner.portfolio.usecase.catalog.model.effectiveClassByTicker
import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class CatalogService(
    private val repository: ICatalogRepository,
    private val aliases: TickerAliasResolver,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    // One row per paper: a renamed paper's old and new rows collapse onto the canonical code, so a
    // class lookup by either code lands on the same entry.
    suspend fun fetchAll(): List<CatalogEntry> {
        val entries = repository.fetchAll()
        val canonicalByTicker = aliases.canonicalAll(entries.map { it.ticker })
        return entries.canonicalEntryByTicker(canonicalByTicker).values.sortedBy { it.ticker }
    }

    suspend fun fetchBy(ticker: String) = repository.fetchBy(aliases.canonical(ticker))

    // Which of the given tickers the catalog has not classified yet. A missing row counts as
    // unclassified: without a class a ticker belongs to no class rung and waits for classification.
    // The returned codes are canonical, the way every other read keys its book.
    suspend fun fetchUnclassified(tickers: Set<String>): Set<String> {
        val canonicalByTicker = aliases.canonicalAll(tickers)
        val classByTicker = fetchAll().effectiveClassByTicker()
        return tickers
            .map { canonicalByTicker.getValue(it) }
            .filter { classByTicker[it] == null }
            .toSet()
    }

    // Upsert the catalog from the latest published COTAHIST file's rows. Unknown BDI codes still
    // enter (AssetKind.UNKNOWN) but are logged so the mapping can be extended instead of silently
    // guessed. A manual classification outlives the refresh: the stored override is carried onto
    // the new entry, so a re-derived BDI class never overwrites the owner's call.
    suspend fun refresh(rows: List<CotahistRow>): Int {
        val overrides = repository.fetchAll().associate { it.ticker to it.assetClassOverride }
        val entries = rows.map { row ->
            row.toCatalogEntry().copy(assetClassOverride = overrides[row.ticker])
        }

        entries.filter { it.assetKind == AssetKind.UNKNOWN }
            .forEach { log.warn("COTAHIST record {} has unclassified BDI type {}", it.ticker, it.bdiType) }

        return repository.upsertAll(entries)
    }

    // Auto-enter a ticker into the catalog (minimal entry) so imports never block on unknown tickers.
    // A retired code resolves to its successor first, so the placeholder never creates a second row
    // for a paper the catalog already knows under the canonical code.
    suspend fun upsertTicker(ticker: String) {
        val normalized = aliases.canonical(ticker)
        val existing = repository.fetchBy(normalized)
        if (existing == null) {
            repository.upsertAll(
                listOf(
                    CatalogEntry(
                        ticker = normalized,
                        shortName = ticker,
                        bdiType = UNCATALOGED_BDI_TYPE,
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

    // The owner's manual classification, outranking whatever the BDI mapping derived. A ticker the
    // catalog has never seen is auto-entered first, so classifying a held ticker never dead-ends.
    suspend fun classify(ticker: String, assetClass: AssetClass): CatalogEntry? {
        if (assetClass == AssetClass.RENDA_FIXA) throw InvalidCatalogClassException(assetClass)

        val canonical = aliases.canonical(ticker)
        upsertTicker(canonical)
        return repository.setAssetClassOverride(canonical, assetClass)
    }

    // Hand the ticker back to the BDI-derived class; the row itself stays in the catalog.
    suspend fun revertClassification(ticker: String): CatalogEntry? =
        repository.setAssetClassOverride(aliases.canonical(ticker), null)

    // The bulk counterpart of classify: the held-unclassified queue's chosen rows in one write. The
    // batch shares one validation and one transaction, so a rejected class or a storage failure
    // leaves every chosen row untouched. A ticker the catalog has never seen is auto-entered first,
    // exactly as the single classify does; the returned entries are the rows actually written.
    suspend fun classifyAll(classifications: Map<String, AssetClass>): List<CatalogEntry> {
        if (classifications.isEmpty()) return emptyList()
        classifications.values.firstOrNull { it == AssetClass.RENDA_FIXA }
            ?.let { throw InvalidCatalogClassException(it) }

        val canonical = aliases.canonicalAll(classifications.keys)
        val normalized = classifications.entries.associate { (ticker, assetClass) ->
            canonical.getValue(ticker) to assetClass
        }
        // Auto-enter any ticker the catalog has never seen, exactly as the single classify does;
        // upsertTicker no-ops on an existing row, so the batch needs no separate existence check.
        for (ticker in normalized.keys) upsertTicker(ticker)
        return repository.setAssetClassOverrides(normalized)
    }

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
