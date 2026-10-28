package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeKey
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import org.springframework.stereotype.Service

@Service
class TradeService(
    private val repository: ITradeRepository,
    private val positionService: PositionService,
    private val gaps: GapService,
    private val transaction: ITransactionTemplate,
) {

    // A manual entry is refused when a broker import already holds the same natural tuple: the
    // imported row is the source of truth and the manual copy would double the position. The insert
    // and the projection it feeds run in one transaction, so a failure cannot leave the ledger and
    // its position disagreeing.
    suspend fun createManual(creation: TradeCreation): Trade {
        val normalized = normalized(creation)
        return transaction.execute {
            val key = normalized.naturalKey()
            val existing = repository.fetchByKey(key)
            if (existing.any { it.source == TradeSource.B3_IMPORT }) throw DuplicateTradeException(key)
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
    suspend fun reconcile(key: TradeKey, creations: List<TradeCreation>) {
        val existing = repository.fetchByKey(key)
        for (occupant in existing.take(creations.size)) {
            if (occupant.source == TradeSource.MANUAL && !occupant.confirmedByB3) {
                repository.confirmByB3(occupant.id)
            }
        }

        val newOccurrences = creations.drop(existing.size)
        for ((index, creation) in newOccurrences.withIndex()) {
            repository.save(normalized(creation), TradeSource.B3_IMPORT, existing.size + index + 1)
        }
        if (newOccurrences.isNotEmpty()) {
            positionService.reproject(key.ticker)
            gaps.reconcile(key.ticker)
        }
    }

    suspend fun fetchAll() = repository.fetchAll()

    suspend fun fetchAllBy(ticker: String) = repository.fetchAllBy(ticker.normalizedTicker())

    // The ticker is the ledger's canonical key, so it is normalized at this boundary (ALUP11F and
    // ALUP11 are the same paper) before the repository persists it.
    private fun normalized(creation: TradeCreation) = creation.copy(ticker = creation.ticker.normalizedTicker())
}
