package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeKey
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerCursor
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerFilter
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerPage
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.model.TradeTickerFilter
import dev.agner.portfolio.usecase.listedasset.model.withCanonicalTicker
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
import org.springframework.stereotype.Service

@Service
class TradeService(
    private val repository: ITradeRepository,
    private val positionService: PositionService,
    private val gaps: GapService,
    private val aliases: TickerAliasResolver,
    private val rightsAllotments: RightsAllotmentService,
    private val transaction: ITransactionTemplate,
) {

    // A manual entry is refused when a broker import already holds the same natural tuple: the
    // imported row is the source of truth and the manual copy would double the position. The new
    // write canonicalizes its ticker at this boundary, so a manual trade filed under a retired code
    // lands on the paper's current identity. The insert and the projection it feeds run in one
    // transaction, so a failure cannot leave the ledger and its position disagreeing.
    suspend fun createManual(creation: TradeCreation): Trade {
        val canonical = aliases.canonical(creation.ticker)
        val normalized = creation.copy(ticker = canonical)
        return transaction.execute {
            val key = normalized.naturalKey()
            // The stored import may carry an old code, so the duplicate check looks across the
            // paper's whole alias set rather than only the canonical spelling.
            val existing = aliases.aliasesOf(canonical)
                .flatMap { repository.fetchByKey(key.withTicker(it)) }
            if (existing.any { it.source == TradeSource.B3_IMPORT }) throw DuplicateTradeException(key)
            // A rights sale with no receipt gets its zero-cost allotment before the sale is written,
            // so the replay sees the receipt first and the position never goes negative.
            if (normalized.side == TradeSide.SELL) {
                rightsAllotments.ensureReceipt(canonical, normalized.date, normalized.quantity)
            }
            val trade = repository.save(normalized, TradeSource.MANUAL, existing.size + 1)
            positionService.reproject(trade.ticker)
            gaps.reconcile(trade.ticker)
            trade
        }
    }

    // Reconciles one natural tuple's occurrences from a batch against the ledger. The ledger's
    // existing occurrences are the ones already applied (their ordinals are contiguous from 1), so
    // skipping them is the dedup — re-exporting content an earlier import already applied is a
    // no-op. A skipped occurrence that is an unconfirmed manual trade is marked confirmed by B3:
    // the manual record wins and keeps its edits. Occurrences past what the ledger holds are new
    // and are written at that occurrence's ordinal, then the ticker's position is reprojected.
    //
    // An import keeps the code the file carried — that is provenance — while the projection it
    // feeds resolves the canonical ticker.
    suspend fun reconcile(key: TradeKey, creations: List<TradeCreation>) {
        val existing = repository.fetchByKey(key)
        for (occupant in existing.take(creations.size)) {
            if (occupant.source == TradeSource.MANUAL && !occupant.confirmedByB3) {
                repository.confirmByB3(occupant.id)
            }
        }

        val newOccurrences = creations.drop(existing.size)
        for ((index, creation) in newOccurrences.withIndex()) {
            if (creation.side == TradeSide.SELL) {
                rightsAllotments.ensureReceipt(creation.ticker, creation.date, creation.quantity)
            }
            repository.save(normalized(creation), TradeSource.B3_IMPORT, existing.size + index + 1)
        }
        if (newOccurrences.isNotEmpty()) {
            positionService.reproject(key.ticker)
            gaps.reconcile(key.ticker)
        }
    }

    // The ledger's occurrence count for a natural tuple — the import review reads the same key
    // apply reconciles against, without writing.
    suspend fun countByKey(key: TradeKey): Int = repository.fetchByKey(key).size

    // Every read presents the paper's canonical ticker, with the imported code kept as provenance.
    suspend fun fetchAll(): List<Trade> {
        val rows = repository.fetchAll()
        return canonicalized(rows)
    }

    suspend fun fetchAllBy(ticker: String): List<Trade> {
        val aliasSet = aliases.aliasesOf(ticker)
        val rows = aliasSet.flatMap { repository.fetchAllBy(it) }
        return canonicalized(rows)
    }

    // The ledger's paged read. The repository returns one extra row, which is the "is there another
    // page" signal; the last row of the page it returns becomes the next cursor. The two aggregates
    // cover the whole filter, so the page can state "n de m" and a total while it loads more. A
    // typed ticker that names a renamed paper widens to its alias set, so the new code finds the
    // rows stored under the old one.
    suspend fun fetchLedger(filter: TradeLedgerFilter): TradeLedgerPage {
        val prepared = preparedFilter(filter)
        val fetched = repository.fetchLedger(prepared)
        val page = fetched.take(prepared.limit)
        val nextCursor = page.lastOrNull()
            ?.let { TradeLedgerCursor(it.date, it.id).encode() }
            ?.takeIf { fetched.size > prepared.limit }
        return TradeLedgerPage(
            rows = canonicalized(page),
            nextCursor = nextCursor,
            total = repository.countLedger(prepared),
            financialTotal = repository.sumLedgerFinancial(prepared),
        )
    }

    private suspend fun preparedFilter(filter: TradeLedgerFilter): TradeLedgerFilter {
        val typed = (filter.ticker as? TradeTickerFilter.Search)
            ?.text
            ?.normalizedTicker()
            ?.takeIf { it.isNotEmpty() }
            ?: return filter.copy(ticker = null)
        val aliasSet = aliases.aliasesOf(typed)
        return filter.copy(
            ticker = if (aliasSet.size > 1) {
                TradeTickerFilter.Aliases(aliasSet.sorted())
            } else {
                TradeTickerFilter.Search(typed)
            },
        )
    }

    private suspend fun canonicalized(rows: List<Trade>): List<Trade> {
        val canonicalByTicker = aliases.canonicalAll(rows.map { it.ticker })
        return rows.map { it.withCanonicalTicker(canonicalByTicker.getValue(it.ticker)) }
    }

    // The ticker is the ledger's canonical key, so it is normalized at this boundary (ALUP11F and
    // ALUP11 are the same paper) before the repository persists it.
    private fun normalized(creation: TradeCreation) = creation.copy(ticker = creation.ticker.normalizedTicker())
}
