package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.PositionDetail
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import org.springframework.stereotype.Service

@Service
class PositionService(
    private val positions: IPositionRepository,
    private val trades: ITradeRepository,
) {

    suspend fun fetchAll() = positions.fetchAll()

    suspend fun fetchBy(ticker: String) = positions.fetchBy(ticker.normalizedTicker())

    // The projection for one ticker, replayed from its ledger and persisted. This is both the
    // update path after a trade insert (the caller wraps it in the same transaction as the insert)
    // and the repair path — a replay from an empty position is idempotent, so re-running it only
    // ever restores what the ledger says.
    suspend fun reproject(ticker: String): Position {
        val normalized = ticker.normalizedTicker()
        val position = PositionCalculator.replay(normalized, trades.fetchAllBy(normalized))
        positions.save(position)
        return position
    }

    // Replays every ticker's ledger, repairing drift across the whole book.
    suspend fun reprojectAll(): List<Position> {
        val reprojected = trades.fetchAll()
            .groupBy { it.ticker }
            .map { (ticker, ledger) -> PositionCalculator.replay(ticker, ledger) }
        for (position in reprojected) positions.save(position)
        return reprojected
    }

    // A read: the stored projection plus the ledger rows it was replayed from. Null when the ticker
    // has no projected position (no trades were ever recorded against it).
    suspend fun fetchDetail(ticker: String): PositionDetail? {
        val normalized = ticker.normalizedTicker()
        val position = positions.fetchBy(normalized) ?: return null
        return PositionDetail(position, trades.fetchAllBy(normalized))
    }
}
