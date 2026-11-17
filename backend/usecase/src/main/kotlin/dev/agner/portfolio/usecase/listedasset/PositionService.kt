package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.commons.weightedAveragePrice
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.PositionDetail
import dev.agner.portfolio.usecase.listedasset.model.withCanonicalTicker
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

// The projected custody read. Every lookup resolves the canonical ticker first, so a buy under a
// retired code and a sell under its successor replay into one position keyed on the current code;
// the old code's stored row is removed rather than left beside it.
@Service
class PositionService(
    private val positions: IPositionRepository,
    private val trades: ITradeRepository,
    private val corporateActions: ICorporateActionRepository,
    private val gaps: IGapRepository,
    private val aliases: TickerAliasResolver,
) {

    suspend fun fetchAll(): List<Position> {
        val stored = positions.fetchAll()
        val canonicalByTicker = aliases.canonicalAll(stored.map { it.ticker })
        return stored
            .groupBy { canonicalByTicker.getValue(it.ticker) }
            .map { (canonical, rows) -> merge(canonical, rows) }
            .sortedBy { it.ticker }
    }

    suspend fun fetchBy(ticker: String) = positions.fetchBy(aliases.canonical(ticker))

    // The projection for one ticker, replayed from the ledger of every code the paper has traded
    // under plus the corporate actions that changed it, then persisted under the canonical ticker.
    // This is both the update path after a trade insert or a corporate action (the caller wraps it
    // in the same transaction) and the repair path — a replay from an empty position is idempotent.
    suspend fun reproject(ticker: String): Position {
        val canonical = aliases.canonical(ticker)
        val aliasSet = aliases.aliasesOf(canonical)
        val position = PositionCalculator.replay(
            canonical,
            aliasSet.flatMap { trades.fetchAllBy(it) },
            aliasSet.flatMap { corporateActions.fetchByTicker(it) },
            aliasSet.flatMap { gaps.fetchOpenBy(it) },
        )
        positions.save(position)
        // A rename's old row is superseded by the canonical projection; keeping both would double
        // the custody a read sees.
        (aliasSet - canonical).forEach { positions.delete(it) }
        return position
    }

    // The holder's position immediately before one corporate action — the basis its cash
    // entitlement is computed on, independent of any trade imported after the event's ex-date.
    suspend fun positionBefore(action: CorporateAction): Position {
        val canonical = aliases.canonical(action.ticker)
        val aliasSet = aliases.aliasesOf(canonical)
        return PositionCalculator.positionBefore(
            canonical,
            aliasSet.flatMap { trades.fetchAllBy(it) },
            aliasSet.flatMap { corporateActions.fetchByTicker(it) },
            action,
            aliasSet.flatMap { gaps.fetchOpenBy(it) },
        )
    }

    // The custody at the end of a date — the quantity an income entitlement is paid on. It replays
    // the paper's whole alias set, so a payment under the old code sees the book the rename moved.
    suspend fun positionOn(ticker: String, date: LocalDate): Position {
        val canonical = aliases.canonical(ticker)
        val aliasSet = aliases.aliasesOf(canonical)
        return PositionCalculator.positionOn(
            canonical,
            aliasSet.flatMap { trades.fetchAllBy(it) },
            aliasSet.flatMap { corporateActions.fetchByTicker(it) },
            date,
            aliasSet.flatMap { gaps.fetchOpenBy(it) },
        )
    }

    // Replays every paper's whole ledger and actions, repairing drift across the whole book.
    suspend fun reprojectAll(): List<Position> {
        val ledger = trades.fetchAll()
        val appliedActions = corporateActions.fetchApplied()
        val openGaps = gaps.fetchAll().filter { it.status == GapStatus.OPEN }
        val canonicalByTicker = aliases.canonicalAll(
            (ledger.map { it.ticker } + appliedActions.map { it.ticker } + openGaps.map { it.ticker }).distinct(),
        )
        val actions = appliedActions.groupBy { canonicalByTicker.getValue(it.ticker) }
        val gapsByCanonical = openGaps.groupBy { canonicalByTicker.getValue(it.ticker) }
        val reprojected = ledger
            .groupBy { canonicalByTicker.getValue(it.ticker) }
            .map { (canonical, rows) ->
                PositionCalculator.replay(
                    canonical,
                    rows,
                    actions[canonical].orEmpty(),
                    gapsByCanonical[canonical].orEmpty(),
                )
            }
        for (position in reprojected) positions.save(position)

        // Remove projections still keyed on a superseded code, so the read never sees two rows for
        // one paper.
        val stored = positions.fetchAll()
        val storedCanonical = aliases.canonicalAll(stored.map { it.ticker })
        stored.map { it.ticker }
            .filter { storedCanonical.getValue(it) != it }
            .forEach { positions.delete(it) }
        return reprojected
    }

    // A read: the stored projection plus the ledger rows it was replayed from. The trades carry the
    // canonical ticker, with their imported code kept as provenance.
    suspend fun fetchDetail(ticker: String): PositionDetail? {
        val canonical = aliases.canonical(ticker)
        val position = positions.fetchBy(canonical) ?: return null
        val ledger = aliases.aliasesOf(canonical).flatMap { trades.fetchAllBy(it) }
        return PositionDetail(position, ledger.map { it.withCanonicalTicker(canonical) })
    }

    // Two stored rows for one canonical paper (a rename that has not reprojected yet) merge into
    // one custody: quantities add and the average is weighted by quantity.
    private fun merge(canonical: String, rows: List<Position>): Position {
        if (rows.size == 1) return rows.single().copy(ticker = canonical)
        val quantity = rows.sumOf { it.quantity }
        val average = weightedAveragePrice(rows.map { it.quantity to it.averagePrice }, AVERAGE_SCALE)
        return Position(ticker = canonical, quantity = quantity, averagePrice = average)
    }

    private companion object {
        const val AVERAGE_SCALE = 6
    }
}
